package net.clementlevallois.nocodefunctionswebservices.vvconversion;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.FunctionNetworkConverter;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.SESSION_ID;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.utils.UnicodeBOMInputStream;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class VosViewerConversionEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.get("/api/" + FunctionNetworkConverter.ENDPOINT_GEXF_TO_VV, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            GEXF2VVRequestParams queryParams = parseGEXF2VVParams(ctx);

            Path gexfPath = APIController.globals.getGexfCompleteFilePath(queryParams.getJobId());
            String gexfAsString;
            if (Files.exists(gexfPath)) {
                gexfAsString = Files.readString(gexfPath, StandardCharsets.UTF_8);
            } else {
                ctx.result("error for conversion to vv, gexf file not found on disk".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gexfAsString);
            converter.setMaxNumberNodes(500);
            converter.setTerminologyData(new Terminology());
            converter.getTerminologyData().setItem(queryParams.getItem());
            converter.getTerminologyData().setItems(queryParams.getItems());
            converter.getTerminologyData().setLink(queryParams.getLink());
            converter.getTerminologyData().setLinks(queryParams.getLinks());
            converter.getTerminologyData().setLink_strength(queryParams.getLinkStrength());
            converter.getTerminologyData().setTotal_link_strength(queryParams.getTotalLinkStrength());
            converter.setMetadataData(new Metadata());
            converter.getMetadataData().setAuthorCanBePlural("");
            converter.getMetadataData().setDescriptionOfData(queryParams.getDescriptionData());
            String graphAsJsonVosViewer = converter.convertToJson();
            if (graphAsJsonVosViewer.isBlank()) {
                ctx.result("error for vv conversion, json file not produced".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
            } else {
                graphAsJsonVosViewer = Json.encodePointer(graphAsJsonVosViewer);
                ctx.result(graphAsJsonVosViewer.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );

        app.post("/api/" + FunctionNetworkConverter.ENDPOINT_VV_TO_GEXF, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            VV2GEXFRequestParams queryParams = parseVV2GEXFParams(ctx);
            Path tempDataPath = APIController.globals.getInputDataPath(queryParams.getJobId());

            if (!Files.exists(tempDataPath)) {
                ctx.status(HttpURLConnection.HTTP_BAD_REQUEST).result("error for conversion to gexf, vv json file not found on disk");
                return;
            }

            try {
                String jsonFixed;
                try (var is = Files.newInputStream(tempDataPath); var ubis = new UnicodeBOMInputStream(is)) {
                    ubis.skipBOM();
                    jsonFixed = new String(ubis.readAllBytes(), StandardCharsets.UTF_8);
                }
                VOSViewerJsonToGexf converter = new VOSViewerJsonToGexf(jsonFixed);
                String gexfAsString = converter.convertToGexf();

                if (gexfAsString == null || gexfAsString.isBlank()) {
                    ctx.status(HttpURLConnection.HTTP_INTERNAL_ERROR).result("error in the conversion of vv json to gexf");
                } else {
                    ctx.status(HttpURLConnection.HTTP_OK).result(gexfAsString);
                }

            } catch (IOException | NullPointerException ex) {
                Exceptions.printStackTrace(ex);
                ctx.status(HttpURLConnection.HTTP_INTERNAL_ERROR).result("error reading vv json file for conversion to gexf");
            }
        });

        return app;
    }

    private static VV2GEXFRequestParams parseVV2GEXFParams(Context ctx) throws Exception {
        var rp = new VV2GEXFRequestParams();
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<Globals.GlobalQueryParams> gqp = APIController.enumValueOf(Globals.GlobalQueryParams.class, key);

            if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
                    case SESSION_ID ->
                        s -> rp.setSessionId(s);
                    case CALLBACK_URL ->
                        s -> rp.setCallbackURL(s);
                    case JOB_ID ->
                        s -> rp.setJobId(s);
                };
                gqpHandler.accept(decodedParamValue);
            } else {
                System.out.println("VV to GEXF endpoint: unknown query param key: " + key);
            }
        }
        return rp;
    }

    private static GEXF2VVRequestParams parseGEXF2VVParams(Context ctx) throws Exception {
        var params = new GEXF2VVRequestParams();
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<FunctionNetworkConverter.GexfToVVQueryParams> qp = APIController.enumValueOf(FunctionNetworkConverter.GexfToVVQueryParams.class, key);
            Optional<Globals.GlobalQueryParams> gqp = APIController.enumValueOf(Globals.GlobalQueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
                    case ITEM ->
                        params::setItem;
                    case ITEMS ->
                        params::setItems;
                    case LINK ->
                        params::setLink;
                    case LINKS ->
                        params::setLinks;
                    case LINK_STRENGTH ->
                        params::setLinkStrength;
                    case TOTAL_LINK_STRENGTH ->
                        params::setTotalLinkStrength;
                    case DESCRIPTION_DATA ->
                        params::setDescriptionData;
                };
                qpHandler.accept(decodedParamValue);
            } else if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
                    case SESSION_ID ->
                        params::setSessionId;
                    case CALLBACK_URL ->
                        params::setCallbackURL;
                    case JOB_ID ->
                        params::setJobId;
                };
                gqpHandler.accept(decodedParamValue);
            } else {
                System.out.println("Gexf To VV endpoint: unknown query param key: " + key);
            }
        }
        return params;
    }

}

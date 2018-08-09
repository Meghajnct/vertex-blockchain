package com.sample.block.hyperledger.main;

import com.sample.block.hyperledger.model.SampleOrg;
import com.sample.block.hyperledger.utils.TestConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.apache.commons.codec.binary.Base64;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.TransactionRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ControllerVerticle extends AbstractVerticle {

    private HttpServer httpServer = null;
    private HFClient client;
    private Channel channel;
    private ChaincodeID chaincodeID;
    private TestConfig testConfig;
    private TransactionRequest.Type CHAIN_CODE_LANG;
    private String TEST_FIXTURES_PATH;
    private SampleOrg sampleOrg;

   // public static Map<String, Block> blockchain = new LinkedHashMap<String, Block>();
    public ControllerVerticle(HFClient client, Channel channel, ChaincodeID chaincodeID,
                              TestConfig testConfig, TransactionRequest.Type CHAIN_CODE_LANG,
                              String TEST_FIXTURES_PATH, SampleOrg sampleOrg){

        this.client=client;
        this.channel=channel;
        this.chaincodeID=chaincodeID;
        this.testConfig=testConfig;
        this.CHAIN_CODE_LANG=CHAIN_CODE_LANG;
        this.TEST_FIXTURES_PATH=TEST_FIXTURES_PATH;
        this.sampleOrg=sampleOrg;

    }

    Service service = new Service();

    public void start(Future<Void> startFuture) {
        httpServer = vertx.createHttpServer();

        httpServer.requestHandler(new Handler<HttpServerRequest>() {

            public void handle(HttpServerRequest request) {
                try {

                    request.response()
                            .putHeader("content-type", "text/plain")
                            .putHeader("Access-Control-Allow-Origin", "*")
                            .putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                            .putHeader("Access-Control-Allow-Headers", "*");

                    System.out.println("incoming request!");
                    System.out.println("uri  :: " + request.uri());
                    System.out.println("path  : " + request.path());
                    System.out.println("request.method() : "+request.method());
                    //test.main();

                    Buffer fullRequestBody = Buffer.buffer();



                    if (request.path().equals("/all")) {
                        request.endHandler(v -> {

                            String startIndex = request.getParam("startIndex");
                            String endIndex = request.getParam("endIndex");
                            System.out.println("startIndex  :: "+startIndex+" endIndex "+endIndex);
                            String resp = service.getAllCandidate(client,channel,chaincodeID,startIndex,endIndex);
                            System.out.println("Responce data :: "+resp);
                            HttpServerResponse response = request.response();
                            response.setStatusCode(200);


                            if(resp.startsWith("[")) {


                                JSONArray jsonArray = new JSONArray(resp);

                                Object datavalue = "";
                                for (Object obj : jsonArray) {
                                    JSONObject jobj = (JSONObject) obj;
                                    datavalue = (jobj.getJSONObject("Record")).get("data");
                                    System.out.println("json object :: " + (datavalue.toString().contains("firstName")));
                                    System.out.println("ID :: " + jobj.getString("Key") + " key :: " + jobj.getString("Key"));
                                    if (!datavalue.toString().contains("firstName"))
                                        datavalue = decodeBase64BinaryToFile(jobj.getJSONObject("Record").getString("data"), jobj.getString("Key"));

                                    jobj.getJSONObject("Record").put("data", datavalue);

                                }
                                response.end(jsonArray.toString());
                            }else
                                response.end(resp);



                           // response.end(resp);

                        });
                    }

                    if (request.path().equalsIgnoreCase("/getCandidate")) {

                            System.out.println("Param : "+request.getParam("candidateID"));

                            request.endHandler(v -> {
                                String data = request.getParam("candidateID");
                                System.out.println("Data :: "+data);
                                String resp = service.getCandidateById(client,channel,chaincodeID,data,"query");
                                HttpServerResponse response = request.response();
                                response.setStatusCode(200);
                                System.out.println("Responce data :: "+resp);
                                JSONObject jsonObject = new JSONObject(resp);
                                Object datavalue ="";
                                    datavalue = jsonObject.get("data");
                                    System.out.println("json object :: "+(datavalue.toString().contains("firstName")));
                                    System.out.println("ID :: "+jsonObject.getString("id"));
                                    if (!datavalue.toString().contains("firstName"))
                                        datavalue = decodeBase64BinaryToFile(jsonObject.getJSONObject("Record").getString("data"),jsonObject.getString("id"));

                                    jsonObject.put("data",datavalue);

                                response.end(jsonObject.toString());

                            });
                    }
                    if (request.path().equalsIgnoreCase("/history")) {

                        System.out.println("Param : "+request.getParam("candidateID"));

                        request.endHandler(v -> {
                            String data = request.getParam("candidateID");
                            System.out.println("Data :: "+data);
                            String resp = service.getCandidateById(client,channel,chaincodeID,data,"history");
                            HttpServerResponse response = request.response();
                            response.setStatusCode(200);
                            System.out.println("Responce data :: "+resp);
                            if(null != resp && resp.contains("[")) {

                                JSONArray jsonArray = new JSONArray(resp);

                                Object datavalue = "";
                                for (Object obj : jsonArray) {
                                    JSONObject jsonobj = (JSONObject) obj;
                                    JSONObject jobj = jsonobj.getJSONObject("Value");
                                    datavalue = jobj.get("data");
                                    if (!datavalue.toString().contains("firstName"))
                                        datavalue = decodeBase64BinaryToFile(jobj.getString("data"), jobj.getString("id"));

                                    jobj.put("data", datavalue);
                                }
                                response.end(jsonArray.toString());
                            }else
                                response.end(resp);

                        });
                    }
                    if (request.path().equalsIgnoreCase("/blockWalker")) {

                        System.out.println("Param : "+request.getParam("candidateID"));

                        request.endHandler(v -> {
                            String data = request.getParam("candidateID");
                            System.out.println("Data :: "+data);
                            service.blockWalker(client,channel);
                            HttpServerResponse response = request.response();
                            response.setStatusCode(200);

                            response.end("success");

                        });
                    }


                        if (request.path().equals("/addCandidate")) {
                            request.handler(new Handler<Buffer>() {
                                @Override
                                public void handle(Buffer buffer) {
                                    fullRequestBody.appendBuffer(buffer);
                                }
                            });
                            System.out.println("incoming request addCandidate ");

                            request.endHandler(v -> {
                                System.out.println("Full body received, length = " + fullRequestBody.length() + "data " + fullRequestBody);

                                String data = fullRequestBody.toString();
                                JSONObject candidate = new JSONObject(data);

                                String[] strArray = getDataArray(candidate);

                                String resp = service.createCandidate( client,  channel,  chaincodeID,
                                         testConfig,  CHAIN_CODE_LANG,
                                         TEST_FIXTURES_PATH,  sampleOrg,strArray);


                                HttpServerResponse response = request.response();
                                response.setStatusCode(200);

                                response.end(resp);

                            });
                        }



                        if (request.path().equals("/updateCandidate")) {
                            request.handler(new Handler<Buffer>() {
                                @Override
                                public void handle(Buffer buffer) {
                                    fullRequestBody.appendBuffer(buffer);
                                }
                            });
                            request.endHandler(v -> {
                                System.out.println("Full body received, length = " + fullRequestBody.length() + "data " + fullRequestBody);
                                System.out.println("sending response : " + fullRequestBody);
                                String data = fullRequestBody.toString();
                                JSONObject candidate = new JSONObject(data);

                                String[] strArray = getDataArray(candidate);

                                service.updateCandidate(client,channel,chaincodeID,testConfig,sampleOrg,strArray);
                                HttpServerResponse response = request.response();
                                response.setStatusCode(200);
                                String resp = "sucesss";
                                response.end(resp);

                            });
                        }

                        if (request.uri().equals("/update")) {
                            request.endHandler(v -> {
                                System.out.println("Full body received, length = " + fullRequestBody.length() + "data " + fullRequestBody);
                                System.out.println("sending response : " + fullRequestBody);
                                String data = fullRequestBody.toString();
                               // String resp = test.createTransaction(data);
                                HttpServerResponse response = request.response();
                                response.setStatusCode(200);

                                //response.end(resp);

                            });
                        }

                        if (request.uri().equals("/block")) {
                            request.endHandler(v -> {
                                System.out.println("Full body received, length = " + fullRequestBody.length() + "data " + fullRequestBody);
                                System.out.println("sending response : " + fullRequestBody);
                                String data = fullRequestBody.toString();
                                System.out.println("Responce data :: "+data);
                               // String resp = test.getBlock(data);
                                HttpServerResponse response = request.response();
                                response.setStatusCode(200);

                                //response.end(resp);

                            });
                        }




                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    System.out.println(" ERROR " + e.getMessage());
                    e.printStackTrace();
                }
            }


        });
        httpServer.listen(9998);
    }

    public String[] getDataArray(JSONObject object){
        System.out.println("in getDataArray JSON :: "+object);
        List<String> list = new ArrayList<String>();
        // order should be shynk with chain code
        list.add(object.getString("Id"));
        list.add(object.getString("refNum"));
        list.add(object.getString("email"));
        Object json = object.get("data");
        System.out.println(" getDataArray :: "+(json instanceof JSONObject));
        System.out.println("data : "+object.get("data"));
       /* if(object.getBoolean("File")){
            String filepath = object.getString("data");
            list.add(this.encodeFileToBase64Binary(filepath));
        }else {*/

            if(json.equals("NULL#")){
               list.add(json.toString());
            }
            else if (json instanceof JSONObject || json.toString().contains("firstName"))
                list.add(object.get("data").toString());
            else {
                String filepath = object.getString("data");
                list.add(this.encodeFileToBase64Binary(filepath));
                //list.add("{" + object.getString("data") + "}");
            }
      //  }

        String[] array = list.toArray(new String[0]);

        System.out.println("in getDataArray JSON :: "+array);
        return array;

    }
    public String[] jsonToArray(JSONObject object){


        List<String> list = new ArrayList<String>();

        int i = 0;
        for(String key : object.keySet()){
            list.add(key);
            list.add(object.getString(key));
        }
        System.out.println("in jsonToArray JSON :: "+list);
        String[] array = list.toArray(new String[0]);

        System.out.println();
        return array;
    }

    private String encodeFileToBase64Binary(String fileName){


        String encodedString = null;
        try{
            File file = new File(fileName);
            byte[] bytes = loadFile(file);
            byte[] encoded = Base64.encodeBase64(bytes);
            encodedString = new String(encoded);
        }catch (IOException ex){
            ex.printStackTrace();
        }


        return encodedString;
    }
    private String decodeBase64BinaryToFile(String encodedString ,String candidateId){
        System.out.println("encodedString: "+encodedString);
        System.out.println("candidateId :"+candidateId);
        byte[] bytes = Base64.decodeBase64(encodedString);
        String path="/home/meghagupta/Downloads/blockchianresumes/"+candidateId+"_resume.docx";

        FileOutputStream stream = null;
        try {
            File file = new File(path);
            if(!file.exists()){
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            stream = new FileOutputStream(path);
            stream.write(bytes);
            stream.close();
        } catch (Exception e){
            e.printStackTrace();

        }finally
        {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return path;
    }
    private static byte[] loadFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
        byte[] bytes = new byte[(int)length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }

        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }

        is.close();
        return bytes;
    }

    @Override
    public void stop(Future stopFuture) throws Exception {
        System.out.println("MyVerticle stopped!");
    }
}

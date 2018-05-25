package com.sample.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

public class MyVerticle extends AbstractVerticle {
	private HttpServer httpServer = null;

    public void start(Future<Void> startFuture) {
    	httpServer = vertx.createHttpServer();
    	
    	httpServer.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
            	System.out.println("incoming request!");

                Buffer fullRequestBody = Buffer.buffer();
                
                if(request.method() == HttpMethod.POST){
                	System.out.println("incoming request Post ");

                    request.handler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer buffer) {
                            fullRequestBody.appendBuffer(buffer);
                        }
                    });
                    request.endHandler(v -> {
                        System.out.println("Full body received, length = " + fullRequestBody.length()+"data "+fullRequestBody);
                        
                        System.out.println("sending response : "+fullRequestBody);
                        HttpServerResponse response = request.response();
                        response.setStatusCode(200);
                        /*response.headers()
                            .add("Content-Length", String.valueOf(157))
                           ;*/
                        
                        //response.write("Vert.x is alive! data ");
                        response.end("Vert.x is alive! data "+fullRequestBody);
                    
                    });
                    
                }
               
                /*HttpServerResponse response = request.response();
                response.setStatusCode(200);
                response.headers()
                    .add("Content-Length", String.valueOf(157))
                   ;
                
                //response.write("Vert.x is alive! data ");
                response.end("Vert.x is alive! data "+fullRequestBody);*/
            }
            
            
        });
    	httpServer.listen(9999);
    }

    @Override
    public void stop(Future stopFuture) throws Exception {
        System.out.println("MyVerticle stopped!");
    }
}

package com.sample.block.hyperledger.main;

import com.google.protobuf.ByteString;
import com.sample.block.hyperledger.model.SampleOrg;
import com.sample.block.hyperledger.utils.TestConfig;
import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidProtocolBufferRuntimeException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.sample.block.hyperledger.main.Main.out;
import static java.lang.String.format;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;
import static org.hyperledger.fabric.sdk.Channel.NOfEvents.createNofEvents;

public class Service {

    private static final String CHANNEL_NAME = "foo";

    private static final String TESTUSER_1_NAME = "user1";
    /*String CHAIN_CODE_FILEPATH = "sdkintegration/gocc/sample1";
    String CHAIN_CODE_NAME = "example_cc_go";
    String CHAIN_CODE_PATH = "github.com/example_cc";
    String CHAIN_CODE_VERSION = "1";*/
    private static final Map<String, String> TX_EXPECTED;

    static {
        TX_EXPECTED = new HashMap<>();
        TX_EXPECTED.put("readset1", "Missing readset for channel bar block 1");
        TX_EXPECTED.put("writeset1", "Missing writeset for channel bar block 1");
    }

    TransactionRequest.Type CHAIN_CODE_LANG = TransactionRequest.Type.GO_LANG;

    private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED_EVENT_NAME = "event";

    public String createCandidate(HFClient client, Channel channel, ChaincodeID chaincodeID,
                             TestConfig testConfig, TransactionRequest.Type CHAIN_CODE_LANG,
                             String TEST_FIXTURES_PATH, SampleOrg sampleOrg, String[] strArray){

       System.out.println("  strArray in chain init method:: "+strArray);
        for (String s : strArray) {
            System.out.println(s);
        }

        try {

            final String channelName = channel.getName();
            boolean isFooChain = CHANNEL_NAME.equals(channelName);

            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();
            System.out.println("PATH :: "+chaincodeID.getPath());

            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            //instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            queryByChaincodeRequest.setChaincodeID(chaincodeID);
            //instantiateProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
            queryByChaincodeRequest.setFcn("add");
            queryByChaincodeRequest.setArgs(strArray);
            Map<String, byte[]> tm = new HashMap<>();
            tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(StandardCharsets.UTF_8));
            tm.put("method", "InstantiateProposalRequest".getBytes(StandardCharsets.UTF_8));
            queryByChaincodeRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
          //  ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
          //  chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
          //  instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

            System.out.println("Sending instantiateProposalRequest to all peers with arguments.... ");
            System.out.println("ARGS :: "+queryByChaincodeRequest.getArgs());
            successful.clear();
            failed.clear();
            responses = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());

            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            System.out.println("Received "+responses.size()+" instantiate proposal responses. Successful+verified: "+successful.size()+" . Failed: "+ failed.size());
            if (failed.size() > 0) {
                for (ProposalResponse fail : failed) {

                    System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + fail.getMessage() + ", on peer" + fail.getPeer());

                }
                ProposalResponse first = failed.iterator().next();
                //System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
            }

            ///////////////
            /// Send instantiate transaction to orderer
            //System.out.println("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" );

            //Specify what events should complete the interest in this transaction. This is the default
            // for all to complete. It's possible to specify many different combinations like
            //any from a group, all from one group and just one from another or even None(NOfEvents.createNoEvents).
            // See. Channel.NOfEvents
            Channel.NOfEvents nOfEvents = createNofEvents();
            if (!channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)).isEmpty()) {
                nOfEvents.addPeers(channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)));
            }
            if (!channel.getEventHubs().isEmpty()) {
                nOfEvents.addEventHubs(channel.getEventHubs());
            }



            // Send Transaction Transaction to orderer
            System.out.println("Sending chaincode transaction to orderer.");
            channel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);


            BlockchainInfo channelInfo = channel.queryBlockchainInfo();
            System.out.println("blockchain data ::"+channelInfo.getBlockchainInfo().getAllFields());
            System.out.println("Channel info for : " + channelName);
            System.out.println("Channel height: " + channelInfo.getHeight());
            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
            System.out.println("Chain current block hash: " + chainCurrentHash);
            System.out.println("Chainl previous block hash: " + chainPreviousHash);

            // Query by block number. Should return latest block, i.e. block number 2
            BlockInfo returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());
            System.out.println("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber()
                    + " \n previous_hash " + previousHash);
            //out(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
            //out(chainPreviousHash, previousHash);
            // Query by block hash. Using latest block's previous hash so should return block number 1
            byte[] hashQuery = returnedBlock.getPreviousHash();
            returnedBlock = channel.queryBlockByHash(hashQuery);
            System.out.println("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
            //out(channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());


        }catch(Exception ex ) {
            ex.printStackTrace();
        }

        return "sucess";
    }




    CompletableFuture<BlockEvent.TransactionEvent> updateCandidate(HFClient client, Channel channel, ChaincodeID chaincodeID,
                                                              TestConfig testConfig,SampleOrg sampleOrg, String[] strArray) {

        try {
            final String channelName = channel.getName();
            System.out.println("strArray in move :: "+ strArray);
            for(String ss : strArray){
                System.out.println(ss);
            }
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();
            User user = sampleOrg.getAdmin();
            ///////////////
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(chaincodeID);
            transactionProposalRequest.setFcn("update");
            transactionProposalRequest.setArgs(strArray);
            transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            if (user != null) { // specific user use that
                transactionProposalRequest.setUserContext(user);
            }
            ///out("sending transaction proposal to all peers with arguments: move(a,b,%s)");

            Collection<ProposalResponse> invokePropResp = channel.sendTransactionProposal(transactionProposalRequest);
            for (ProposalResponse response : invokePropResp) {
                if (response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                    System.out.println("Successful transaction proposal response Txid: "+response.getTransactionID()+" from peer :"+ response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            System.out.println("Received "+ invokePropResp.size()+" transaction proposal responses. Successful verified :"+ successful.size() +" Failed: %d"+
                   failed.size());
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();

                throw new ProposalException(format("Not enough endorsers for invoke(move a,b,%s):%d endorser error:%s. Was verified:%b",
                        firstTransactionProposalResponse.getStatus().getStatus(), firstTransactionProposalResponse.getMessage(), firstTransactionProposalResponse.isVerified()));

            }
            System.out.println("Successfully received transaction proposal responses.");

            ////////////////////////////
            // Send transaction to orderer
           // System.out.println("Sending chaincode transaction(move a,b,%s) to orderer.");
            if (user != null) {
                return channel.sendTransaction(successful, user);
            }

            for (ProposalResponse response : invokePropResp) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    System.out.println("Succesful instantiate proposal response Txid: "+response.getTransactionID()+" from peer "+ response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            System.out.println("Received "+invokePropResp.size()+"instantiate proposal responses. Successful+verified: "+successful.size()+" . Failed:"+ failed.size());
            if (failed.size() > 0) {
                for (ProposalResponse fail : failed) {

                    System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + fail.getMessage() + ", on peer" + fail.getPeer());

                }
                ProposalResponse first = failed.iterator().next();
                //System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
            }
            return channel.sendTransaction(successful);
        } catch (Exception e) {

            e.printStackTrace();
            return null;
        }


    }
    public String getCandidateById(HFClient client, Channel channel, ChaincodeID chaincodeID,
                               String candidateId,String fun){
        String expect = "" ;
        System.out.println("strArray "+candidateId);
        try {

            System.out.println("### getCandidateById ### ");
            System.out.println("REQUEST ## \n channel :"+channel.getName() +" \n clinet "+client.getUserContext());

            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(new String[] {candidateId});
            queryByChaincodeRequest.setFcn(fun);
            queryByChaincodeRequest.setChaincodeID(chaincodeID);

            System.out.println("PATH  Deployed Chaincode :: "+chaincodeID.getPath());

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(StandardCharsets.UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(StandardCharsets.UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);

            System.out.println("channel.getPeers() "+channel.getPeers());
            System.out.println("queryByChaincodeRequest "+queryByChaincodeRequest.getTransientMap());

            Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
							/*fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
									". Messages: " + proposalResponse.getMessage()
									+ ". Was verified : " + proposalResponse.isVerified());*/
                    System.out.println("proposalResponse.getStatus()  :"+proposalResponse.getStatus());
                    System.out.println("proposalResponse.getProposal() :"+proposalResponse.getProposal());
                } else {
                    String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    System.out.println("Query payload  from peer "+proposalResponse.getPeer().getName()+" returned :: "+ payload);
                   // JSONParser parser = new JSONParser();
                   // JSONObject json = (JSONObject) parser.parse(payload);
                  //  JSONObject jsonObj = new JSONObject(payload);
                    System.out.println("payload :: "+payload);
                    /*proposalResponse.getProposalResponse().getUnknownFields().asMap().forEach((k,v)->{
                        System.out.println("k: "+k);
                        System.out.println("v: "+v);
                    });
                    System.out.println(" ############ proposalResponse.getProposalResponse().getAllFields() ########");
                    proposalResponse.getProposalResponse().getAllFields().forEach((key,value) -> {
                        System.out.println("Key ::"+key +" value :"+value);
                    });

                    System.out.println(" ############  proposalResponse.getProposalResponse().getResponse().getAllFields() ########");

                    proposalResponse.getProposalResponse().getResponse().getAllFields().forEach((k,v)->{
                        System.out.println("k: "+k);
                        System.out.println("v: "+v);
                    });*/
                    expect = payload;
                }
            }


            BlockchainInfo channelInfo = channel.queryBlockchainInfo();

            System.out.println("\n\n  ### RESPONSE ### ");

            System.out.println("blockchain data :: "+channelInfo.getBlockchainInfo().getAllFields());
            System.out.println("Channel info for : " + channel.getName());
            System.out.println("Channel height: " + channelInfo.getHeight());
            System.out.println("channelInfo.getBlockchainInfo().getAllFields()  :: "+channelInfo.getBlockchainInfo().getAllFields());

            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
            System.out.println("Chain current block hash : " + chainCurrentHash);
            System.out.println("Chain previous block hash : " + chainPreviousHash);

            // Query by block number. Should return latest block, i.e. block number 2
            BlockInfo returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());
            returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
            System.out.println(" returnedBlock "+returnedBlock);
            System.out.println("block :: "+returnedBlock.getBlock().toString());
            System.out.println(" block : "+returnedBlock.getTransactionCount());
            System.out.println(returnedBlock.getDataHash());
            /*returnedBlock.getEnvelopeInfos().forEach(envelopeInfo -> {
                System.out.println(envelopeInfo.getType());
                System.out.println(envelopeInfo.getTransactionID());
            });*/

            System.out.println("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber());
            //System.out.println(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
            //System.out.println(chainPreviousHash, previousHash);
            // Query by block hash. Using latest block's previous hash so should return block number 1
           // byte[] hashQuery = returnedBlock.getPreviousHash();
          //  returnedBlock = channel.queryBlockByHash(hashQuery);
         //   System.out.println("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
            //System.out.println(channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());


            return expect;
        } catch (Exception e) {
            System.out.println("Caught exception while running query");
            e.printStackTrace();
            //fail("Failed during chaincode query with error : " + e.getMessage());
        }
        return expect;
    }


    public String getAllCandidate(HFClient client, Channel channel, ChaincodeID chaincodeID,
                                   String startindex, String endIndex){
        String expect = "" ;
        System.out.println(" getAllCandidate strArray "+startindex+"  endIndex "+endIndex);
        try {

            System.out.println("### getCandidateById ### ");
            System.out.println("REQUEST ## \n channel :"+channel.getName() +" \n clinet "+client.getUserContext());

            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(new String[] {startindex,endIndex});
            queryByChaincodeRequest.setFcn("queryAll");
            queryByChaincodeRequest.setChaincodeID(chaincodeID);
            System.out.println("PATH  Deployed Chaincode :: "+chaincodeID.getPath());

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(StandardCharsets.UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(StandardCharsets.UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);

            System.out.println("channel.getPeers() "+channel.getPeers());
            System.out.println("queryByChaincodeRequest "+queryByChaincodeRequest.getTransientMap());

            Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
							/*fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
									". Messages: " + proposalResponse.getMessage()
									+ ". Was verified : " + proposalResponse.isVerified());*/
                    System.out.println("proposalResponse.getStatus()  "+proposalResponse.getStatus());
                    System.out.println(proposalResponse.getProposal());
                } else {
                    System.out.println("proposalResponse.getStatus()  "+proposalResponse.getStatus());
                    System.out.println(proposalResponse.getProposal());
                    String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    System.out.println("Message :: "+ proposalResponse.getProposalResponse().getResponse().getMessage());
                    System.out.println( "getAllFields().keySet() : "+proposalResponse.getProposalResponse().getResponse().getAllFields().keySet());
                    System.out.println("Query payload  from peer "+ proposalResponse.getPeer().getName()+" returned  : "+payload);
                    // out(payload, expect);
                    expect = payload;
                }
            }


            BlockchainInfo channelInfo = channel.queryBlockchainInfo();
            System.out.println("\n\n  ### RESPONSE ### ");
            System.out.println("blockchain data :: "+channelInfo.getBlockchainInfo().getAllFields().keySet());
            System.out.println("Channel info for : " + channel.getName());
            System.out.println("Channel height: " + channelInfo.getHeight());
            System.out.println("channelInfo.getBlockchainInfo().getAllFields()  :: "+channelInfo.getBlockchainInfo().getAllFields());

            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
            System.out.println("Chain current block hash : " + chainCurrentHash);
            System.out.println("Chain previous block hash : " + chainPreviousHash);

            // Query by block number. Should return latest block, i.e. block number 2
            BlockInfo returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
            System.out.println(" returnedBlock "+returnedBlock);
            System.out.println(" block : "+returnedBlock.getTransactionCount());
            returnedBlock.getEnvelopeInfos().forEach(envelopeInfo -> {
                System.out.println(envelopeInfo.getType());
                System.out.println(envelopeInfo.getTransactionID());
            });
            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());

            System.out.println("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber());
            //System.out.println(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
            //System.out.println(chainPreviousHash, previousHash);
            // Query by block hash. Using latest block's previous hash so should return block number 1
 //           byte[] hashQuery = returnedBlock.getPreviousHash();
//            returnedBlock = channel.queryBlockByHash(hashQuery);
 //           System.out.println("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
            //System.out.println(channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());


            return expect;
        } catch (Exception e) {
            System.out.println("Caught exception while running query");
            e.printStackTrace();
            //fail("Failed during chaincode query with error : " + e.getMessage());
            return expect;
        }finally {
            return expect;
        }

    }

    public JSONArray blockWalker(HFClient client, Channel channel)  {
        JSONArray jsonarry = new JSONArray();
        try {
            BlockchainInfo channelInfo = channel.queryBlockchainInfo();

            JSONObject object = null;


            for (long current = channelInfo.getHeight() - 1; current > -1; --current) {
                object = new JSONObject();

                BlockInfo returnedBlock = channel.queryBlockByNumber(current);
                final long blockNumber = returnedBlock.getBlockNumber();

                System.out.println(" ####################################### Block Info"+blockNumber+" #####################################");

                object.put("blockNumber",blockNumber);
                object.put("dataHash",Hex.encodeHexString(returnedBlock.getDataHash()));
                object.put("previoushash",Hex.encodeHexString(returnedBlock.getPreviousHash()));

                out("returnedBlock.getTransactionCount() "+ returnedBlock.getTransactionCount());

                out("current block number %d has data hash: %s", blockNumber, Hex.encodeHexString(returnedBlock.getDataHash()));
                out("current block number %d has previous hash id: %s", blockNumber, Hex.encodeHexString(returnedBlock.getPreviousHash()));
                out("current block number %d has calculated block hash is %s", blockNumber, Hex.encodeHexString(SDKUtils.calculateBlockHash(client,
                        blockNumber, returnedBlock.getPreviousHash(), returnedBlock.getDataHash())));
                JSONArray datajsonarry = new JSONArray();
                for(ByteString bytes : returnedBlock.getBlock().getData().getDataList()){
                    datajsonarry.put(bytes.toString("UTF-8"));
                    System.out.println("Block data :: "+bytes.toString("UTF-8"));
                }
                object.put("UserData",datajsonarry);

                final int envelopeCount = returnedBlock.getEnvelopeCount();
               // out(1, envelopeCount);
                out("current block number %d has %d envelope count:", blockNumber, envelopeCount);
                int i = 0;
                int transactionCount = 0;
                JSONArray dataEnveloparry = new JSONArray();
                object.put("Envelop Data ",dataEnveloparry);
                JSONObject envelopObj = null;
                for (BlockInfo.EnvelopeInfo envelopeInfo : returnedBlock.getEnvelopeInfos()) {
                    ++i;
                    envelopObj = new JSONObject();
                    dataEnveloparry.put(envelopObj);
                    envelopObj.put("transactionId",envelopeInfo.getTransactionID());
                    envelopObj.put("channelId",envelopeInfo.getChannelId());
                    out("  Transaction number %d has transaction id: %s", i, envelopeInfo.getTransactionID());
                    final String channelId = envelopeInfo.getChannelId();
                   // assertTrue("foo".equals(channelId) || "bar".equals(channelId));

                    out("  Transaction number %d has channel id: %s", i, channelId);
                    out("  Transaction number %d has epoch: %d", i, envelopeInfo.getEpoch());
                    out("  Transaction number %d has transaction timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
                    out("  Transaction number %d has type id: %s", i, "" + envelopeInfo.getType());
                    out("  Transaction number %d has nonce : %s", i, "" + Hex.encodeHexString(envelopeInfo.getNonce()));
                    //out("  Transaction number %d has submitter mspid: %s,  certificate: %s", i, envelopeInfo.getCreator().getMspid(), envelopeInfo.getCreator().getId());

                    if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) {
                        ++transactionCount;
                        BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;
                        envelopObj.put("Transaction numbe",i);
                        envelopObj.put("actions ",transactionEnvelopeInfo.getTransactionActionInfoCount());
                        envelopObj.put(" transaction isValid ",transactionEnvelopeInfo.isValid());
                        out("  Transaction number %d has %d actions", i, transactionEnvelopeInfo.getTransactionActionInfoCount());
                       // out(1, transactionEnvelopeInfo.getTransactionActionInfoCount()); // for now there is only 1 action per transaction.
                        out("  Transaction number %d isValid %b", i, transactionEnvelopeInfo.isValid());
                      //  out(transactionEnvelopeInfo.isValid(), true);
                        out("  Transaction number %d validation code %d", i, transactionEnvelopeInfo.getValidationCode());
                       // out(0, transactionEnvelopeInfo.getValidationCode());

                        int j = 0;
                        JSONArray datatransparry = new JSONArray();
                        envelopObj.put("Transactions ",dataEnveloparry);
                        JSONObject transObj = null;
                        for (BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo transactionActionInfo : transactionEnvelopeInfo.getTransactionActionInfos()) {
                            datatransparry.put(transObj);
                            transObj = new JSONObject();
                            transObj.put("Transaction action",j);
                            transObj.put("response status",transactionActionInfo.getResponseStatus());
                            transObj.put("response message",printableString(new String(transactionActionInfo.getResponseMessageBytes(), "UTF-8")));
                            transObj.put("endorsements count ",transactionActionInfo.getEndorsementsCount());
                            ++j;
                            out("   Transaction action %d has response status %d", j, transactionActionInfo.getResponseStatus());
                           // out(200, transactionActionInfo.getResponseStatus());
                            out("   Transaction action %d has response message bytes as string: %s", j,
                                    printableString(new String(transactionActionInfo.getResponseMessageBytes(), "UTF-8")));
                            out("   Transaction action %d has %d endorsements", j, transactionActionInfo.getEndorsementsCount());
                            //out(2, transactionActionInfo.getEndorsementsCount());

                           /* for (int n = 0; n < transactionActionInfo.getEndorsementsCount(); ++n) {
                                BlockInfo.EndorserInfo endorserInfo = transactionActionInfo.getEndorsementInfo(n);
                                out("Endorser %d signature: %s", n, Hex.encodeHexString(endorserInfo.getSignature()));
                                out("Endorser %d endorser: mspid %s \n certificate %s", n, endorserInfo.getMspid(), endorserInfo.getId());
                            }*/
                            JSONArray datachainAry = new JSONArray();
                            transObj.put("ChainCode ",datachainAry);
                            JSONObject chainObj = null;
                            datachainAry.put(chainObj);
                            out("   Transaction action %d has %d chaincode input arguments", j, transactionActionInfo.getChaincodeInputArgsCount());
                            for (int z = 0; z < transactionActionInfo.getChaincodeInputArgsCount(); ++z) {
                                chainObj = new JSONObject();
                                chainObj.put("trancation action ",j);
                                chainObj.put("chaincode input arg ",printableString(new String(transactionActionInfo.getChaincodeInputArgs(z), "UTF-8")) );
                                out("     Transaction action %d has chaincode input argument %d is: %s", j, z,
                                        printableString(new String(transactionActionInfo.getChaincodeInputArgs(z), "UTF-8")));
                            }

                            out("   Transaction action %d proposal response status: %d", j,
                                    transactionActionInfo.getProposalResponseStatus());
                            out("   Transaction action %d proposal response payload: %s", j,
                                    printableString(new String(transactionActionInfo.getProposalResponsePayload())));

                         //   String chaincodeIDName = transactionActionInfo.getChaincodeIDName();
                         //   String chaincodeIDVersion = transactionActionInfo.getChaincodeIDVersion();
                         //   String chaincodeIDPath = transactionActionInfo.getChaincodeIDPath();
                         /*   out("   Transaction action %d proposal chaincodeIDName: %s, chaincodeIDVersion: %s,  chaincodeIDPath: %s ", j,
                                    chaincodeIDName, chaincodeIDVersion, chaincodeIDPath);
*/
                            // Check to see if we have our expected event.
                            if (blockNumber == 2) {
                                ChaincodeEvent chaincodeEvent = transactionActionInfo.getEvent();
                                out("chaincodeEvent",chaincodeEvent);

                                out("Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEvent.getPayload()) : ",Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEvent.getPayload()));
                                out("testTxID", chaincodeEvent.getTxId());
                                out("CHAIN_CODE_NAME", chaincodeEvent.getChaincodeId());
                                out(EXPECTED_EVENT_NAME, chaincodeEvent.getEventName());
                               /* out("CHAIN_CODE_NAME", chaincodeIDName);
                                out("github.com/example_cc", chaincodeIDPath);
                                out("1", chaincodeIDVersion);*/

                            }

                            TxReadWriteSetInfo rwsetInfo = transactionActionInfo.getTxReadWriteSet();
                            if (null != rwsetInfo) {
                                out("   Transaction action %d has %d name space read write sets", j, rwsetInfo.getNsRwsetCount());

                                for (TxReadWriteSetInfo.NsRwsetInfo nsRwsetInfo : rwsetInfo.getNsRwsetInfos()) {
                                    final String namespace = nsRwsetInfo.getNamespace();
                                    KvRwset.KVRWSet rws = nsRwsetInfo.getRwset();

                                    int rs = -1;
                                    for (KvRwset.KVRead readList : rws.getReadsList()) {
                                        rs++;

                                        out("     Namespace %s read set %d key %s  version [%d:%d]", namespace, rs, readList.getKey(),
                                                readList.getVersion().getBlockNum(), readList.getVersion().getTxNum());
                                        System.out.println(" Read set :: "+readList);

                                        if ("bar".equals(channelId) && blockNumber == 2) {
                                            if ("example_cc_go".equals(namespace)) {
                                                if (rs == 0) {
                                                    out("a", readList.getKey());
                                                    out("readList.getVersion().getBlockNum() : ", readList.getVersion().getBlockNum());
                                                    out(" readList.getVersion().getTxNum() : ", readList.getVersion().getTxNum());
                                                } else if (rs == 1) {
                                                    out("b : readList.getKey()", readList.getKey());
                                                    out("readList.getVersion().getBlockNum()", readList.getVersion().getBlockNum());
                                                    out("readList.getVersion().getTxNum()", readList.getVersion().getTxNum());
                                                } else {
                                                    out(format("unexpected readset %d", rs));
                                                }

                                                TX_EXPECTED.remove("readset1");
                                            }
                                        }
                                    }

                                    rs = -1;
                                    for (KvRwset.KVWrite writeList : rws.getWritesList()) {
                                        rs++;
                                        String valAsString = printableString(new String(writeList.getValue().toByteArray(), "UTF-8"));

                                        out("     Namespace %s write set %d key %s has value '%s' ", namespace, rs,
                                                writeList.getKey(),
                                                valAsString);

                                        if ("bar".equals(channelId) && blockNumber == 2) {
                                            if (rs == 0) {
                                                out("a", writeList.getKey());
                                                out("400", valAsString);
                                            } else if (rs == 1) {
                                                out("b", writeList.getKey());
                                                out("400", valAsString);
                                            } else {
                                                out(format("unexpected writeset %d", rs));
                                            }

                                            TX_EXPECTED.remove("writeset1");
                                        }
                                    }
                                }
                            }
                        }
                    }


                }
                jsonarry.put(object);
            }
            if (!TX_EXPECTED.isEmpty()) {
                out(TX_EXPECTED.get(0));
            }
        } catch (InvalidProtocolBufferRuntimeException e) {
            e.printStackTrace();
            //throw e.getCause();
        }catch(Exception e){
            e.printStackTrace();
        }
        return jsonarry;
    }

    static String printableString(final String string) {
        int maxLogStringLength = 64;
        if (string == null || string.length() == 0) {
            return string;
        }

        String ret = string.replaceAll("[^\\p{Print}]", "?");

        ret = ret.substring(0, Math.min(ret.length(), maxLogStringLength)) + (ret.length() > maxLogStringLength ? "..." : "");

        return ret;

    }
}

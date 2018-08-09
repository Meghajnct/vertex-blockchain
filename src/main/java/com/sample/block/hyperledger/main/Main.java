package com.sample.block.hyperledger.main;


import com.google.common.io.CharStreams;
import com.sample.block.hyperledger.model.SampleOrg;
import com.sample.block.hyperledger.model.SampleStore;
import com.sample.block.hyperledger.model.SampleUser;
import com.sample.block.hyperledger.model.ChaincodeEventCapture;
import com.sample.block.hyperledger.utils.TestConfig;
import com.sample.block.hyperledger.utils.TestConfigHelper;
import com.sample.block.hyperledger.utils.TestUtils;
import com.sample.block.hyperledger.utils.Util;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.Charsets;
import org.bouncycastle.openssl.PEMWriter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.Channel.NOfEvents.createNofEvents;
import static org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions;
import static org.hyperledger.fabric.sdk.Channel.TransactionOptions.createTransactionOptions;

public class Main {
	
	
	    private static final String TEST_ADMIN_NAME = "admin";
	    private static final String TESTUSER_1_NAME = "user1";
	    private static final String TEST_FIXTURES_PATH = "/home/meghagupta/dev/WorkingCode/fabric-sdk-java/src/test/fixture";
        private static final String CANDIDATE_CHANNEL_TX_PATH ="/home/meghagupta/dev/WorkingCode/fabric-sdk-java/src/test/fixture/sdkintegration/e2e-2Orgs/v1.2/foo.tx";
				//"/home/meghagupta/dev/WorkingCode/fabric-sdk-java/src/test/fixture/sdkintegration/e2e-2Orgs/v1.2/foo.tx";
	// "/home/meghagupta/dev/blockchain-dev/java/fabric-sample/fabric-samples/channel-artifacts/candidatechannel.tx";

	    private static final String CHANNEL_NAME = "foo";
	    private static final String CANDIDATE_CHANNEL_NAME="foo";//candidateChannel
		private static final String BAR_CHANNEL_NAME = "bar";
		public HFClient client = null;
		public Channel fooChannel = null;
		public SampleOrg sampleOrg = null ;

		public static ChaincodeID chaincodeID = null;

		private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
	    private static final String EXPECTED_EVENT_NAME = "event";
	    private static final Map<String, String> TX_EXPECTED = null;


	    String CHAIN_CODE_FILEPATH = "sdkintegration/gocc/sample1";
	    String CHAIN_CODE_NAME = "example_cc_go";
	    String CHAIN_CODE_PATH = "github.com/example_cc";
	    String CHAIN_CODE_VERSION = "1";
		Type CHAIN_CODE_LANG = Type.GO_LANG;

	    private final TestConfigHelper configHelper = new TestConfigHelper();
	    private Collection<SampleOrg> testSampleOrgs;
	    private static final TestConfig testConfig = TestConfig.getConfig();

		SampleStore sampleStore = null;

		Map<String, Properties> clientTLSProperties = new HashMap<>();

		File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");


	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Main main = new Main();
		try {
			main.checkConfig();
			main.setup();

		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public void checkConfig() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException, org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException {
		System.out.println("\n\n\nRUNNING: %s.\n"+"checkConfig" );
        //   configHelper.clearConfig();
        //   out(256, Config.getConfig().getSecurityLevel());
		TestUtils.resetConfig();
        configHelper.customizeConfig();

        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : testSampleOrgs) {


            String caName = sampleOrg.getCAName(); //Try one of each name and no name.
            if (caName != null && !caName.isEmpty()) {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(caName, sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            } else {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            }

			System.out.println("sampleOrg : "+sampleOrg);
        }
		System.out.println("End check config .. ");
    }

	public void setup() throws Exception {
		//Persistence is not part of SDK. Sample file store is for demonstration purposes only!
		//   MUST be replaced with more robust application implementation  (Database, LDAP)
		System.out.println(" starting setup .. ");
		/*if (sampleStoreFile.exists()) { //For testing start fresh
			sampleStoreFile.delete();
		}*/
		sampleStore = new SampleStore(sampleStoreFile);
		enrollUsersSetup(sampleStore); //This enrolls users with fabric ca and setups sample store to get users later.
		System.out.println(" Registration Process completed .. ");
		runFabricTest(sampleStore); //Runs Fabric tests with constructing channels, joining peers, exercising chaincode

		System.out.println(" Ending setup .. ");

	}

	/**
	 * Will register and enroll users persisting them to samplestore.
	 *
	 * @param sampleStore
	 * @throws Exception
	 */
	public void enrollUsersSetup(SampleStore sampleStore) throws Exception {

		System.out.println(" Enroll User "+sampleStore);
		////////////////////////////
		//Set up USERS

		//SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface

		////////////////////////////
		// get users for all orgs

		for (SampleOrg sampleOrg : testSampleOrgs) {
			try {
				HFCAClient ca = sampleOrg.getCAClient();

				final String orgName = sampleOrg.getName();
				final String mspid = sampleOrg.getMSPID();
				ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

				if (testConfig.isRunningFabricTLS()) {
					//This shows how to get a client TLS certificate from Fabric CA
					// we will use one client TLS certificate for orderer peers etc.
					final EnrollmentRequest enrollmentRequestTLS = new EnrollmentRequest();
					enrollmentRequestTLS.addHost("localhost");
					enrollmentRequestTLS.setProfile("tls");
					final Enrollment enroll = ca.enroll("admin", "adminpw", enrollmentRequestTLS);
					final String tlsCertPEM = enroll.getCert();
					final String tlsKeyPEM = getPEMStringFromPrivateKey(enroll.getKey());

					final Properties tlsProperties = new Properties();

					tlsProperties.put("clientKeyBytes", tlsKeyPEM.getBytes(UTF_8));
					tlsProperties.put("clientCertBytes", tlsCertPEM.getBytes(UTF_8));
					clientTLSProperties.put(sampleOrg.getName(), tlsProperties);
					//Save in samplestore for follow on tests.
					sampleStore.storeClientPEMTLCertificate(sampleOrg, tlsCertPEM);
					sampleStore.storeClientPEMTLSKey(sampleOrg, tlsKeyPEM);
				}

				HFCAInfo info = ca.info(); //just check if we connect at all.

				String infoName = info.getCAName();
				if (infoName != null && !infoName.isEmpty()) {
					System.out.println("ca.getCAName() :: " + ca.getCAName() + "  infoName :: " + infoName);
				}

				SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
				if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
					admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
					admin.setMspId(mspid);
				}

				sampleOrg.setAdmin(admin); // The admin of this org --

				SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, sampleOrg.getName());
				if (!user.isRegistered()) {  // users need to be registered AND enrolled
					RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
					//System.out.println("ca.getHFCAAffiliations(user)  " +ca.getHFCAAffiliations(user));

					user.setEnrollmentSecret(ca.register(rr, admin));
				}
				if (!user.isEnrolled()) {
					user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
					user.setMspId(mspid);
				}
				sampleOrg.addUser(user); //Remember user belongs to this Org

				final String sampleOrgName = sampleOrg.getName();
				final String sampleOrgDomainName = sampleOrg.getDomainName();

				// src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/

				SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
						Util.findFileSk(Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/",
								sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
						Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
								format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());

				sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode

				System.out.println(" sample ORG after setup :: " + sampleOrg.toString());
			}catch(Exception e){
				e.printStackTrace();
			}

		}
		System.out.println(" User enrolled .. ");
	}

	public void runFabricTest(final SampleStore sampleStore) throws Exception {

		////////////////////////////
		// Setup client

		//Create instance of client.
		client = HFClient.createNewInstance();

		client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

		////////////////////////////
		//Construct and run the channels
		System.out.println(" Running TEST ..runFabricTest... ");

		sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
		System.out.println("runFabricTest ## sampleOrg ::  "+sampleOrg);

		if(fooChannel == null ||  !fooChannel.isInitialized()){
			fooChannel = constructChannel(CANDIDATE_CHANNEL_NAME, client, sampleOrg);
			//
		}
		//fooChannel = sampleStore.getChannel(client,"foo");
		//sampleStore.saveChannel(fooChannel);
		runChannel(client, fooChannel, true, sampleOrg, 0);

		Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1));
		vertx.deployVerticle(new ControllerVerticle(client,fooChannel,chaincodeID,testConfig,CHAIN_CODE_LANG,TEST_FIXTURES_PATH,sampleOrg));

	}
	Channel constructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
		////////////////////////////
		//Construct the channel
		//

		System.out.println("Constructing channel %s : "+ name);

		//boolean doPeerEventing = false;
		boolean doPeerEventing = !testConfig.isRunningAgainstFabric10() && BAR_CHANNEL_NAME.equals(name);
        //boolean doPeerEventing = !testConfig.isRunningAgainstFabric10() && CHANNEL_NAME.equals(name);
		//Only peer Admin org
		client.setUserContext(sampleOrg.getPeerAdmin());

		Collection<Orderer> orderers = new LinkedList<>();

		for (String orderName : sampleOrg.getOrdererNames()) {

			Properties ordererProperties = testConfig.getOrdererProperties(orderName);

			//example of setting keepAlive to avoid timeouts on inactive http2 connections.
			// Under 5 minutes would require changes to server side to accept faster ping rates.
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[] {true});


			orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
					ordererProperties));
		}

		//Just pick the first orderer in the list to create the channel.

		Orderer anOrderer = orderers.iterator().next();
		orderers.remove(anOrderer);

		File file = new File(CANDIDATE_CHANNEL_TX_PATH);//new File(TEST_FIXTURES_PATH + "/sdkintegration/e2e-2Orgs/" + TestConfig.FAB_CONFIG_GEN_VERS + "/" + name + ".tx");
		InputStream is = new FileInputStream(file);

		ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(CANDIDATE_CHANNEL_TX_PATH));
		Channel newChannel = client.getChannel(name);
		//Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.

		File channelfile = new File("/tmp/serialisedChannel.txt");

		if(channelfile.exists() && channelfile.length()>1 ){
			byte[] bFile = new byte[(int) channelfile.length()];
			FileInputStream fileInputStream = new FileInputStream(channelfile);
			fileInputStream.read(bFile);
			return client.deSerializeChannel(bFile).initialize();
		}

		if(null == newChannel){
			newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));

		}


		System.out.println("Created channel %s"+ name);

		boolean everyother = true; //test with both cases when doing peer eventing.
		for (String peerName : sampleOrg.getPeerNames()) {
			String peerLocation = sampleOrg.getPeerLocation(peerName);

			Properties peerProperties = testConfig.getPeerProperties(peerName); //test properties for peer.. if any.
			if (peerProperties == null) {
				peerProperties = new Properties();
			}


			//Example of setting specific options on grpc's NettyChannelBuilder
			peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

			Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
			if (doPeerEventing && everyother) {
				newChannel.joinPeer(peer, createPeerOptions()); //Default is all roles.
			} else {
				// Set peer to not be all roles but eventing.
				newChannel.joinPeer(peer, createPeerOptions().setPeerRoles(Peer.PeerRole.NO_EVENT_SOURCE));
			}
			System.out.println("Peer %s joined channel %s"+ peerName+" name "+ name);
			everyother = !everyother;
		}
		//just for testing ...
		if (doPeerEventing) {
			// Make sure there is one of each type peer at the very least.
			System.out.println(newChannel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)).isEmpty());
			System.out.println(newChannel.getPeers(Peer.PeerRole.NO_EVENT_SOURCE).isEmpty());
		}

		for (Orderer orderer : orderers) { //add remaining orderers if any.
			newChannel.addOrderer(orderer);
		}

		for (String eventHubName : sampleOrg.getEventHubNames()) {

			final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);

			eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
			eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});


			EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
					eventHubProperties);
			newChannel.addEventHub(eventHub);
		}

		newChannel.initialize();

		System.out.println("Finished initialization channel %s"+ name);

		//Just checks if channel can be serialized and deserialized .. otherwise this is just a waste :)
		byte[] serializedChannelBytes = newChannel.serializeChannel();
		newChannel.shutdown(true);
		/*if(!channelfile.exists())
			channelfile.createNewFile();*/
		FileOutputStream fos = new FileOutputStream(channelfile);
		fos.write(serializedChannelBytes);
		fos.close();


		return client.deSerializeChannel(serializedChannelBytes).initialize();

	}

	static String getPEMStringFromPrivateKey(PrivateKey privateKey) throws IOException {
		StringWriter pemStrWriter = new StringWriter();
		PEMWriter pemWriter = new PEMWriter(pemStrWriter);

		pemWriter.writeObject(privateKey);

		pemWriter.close();

		return pemStrWriter.toString();
	}

	void runChannel(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, int delta) {

		Vector<ChaincodeEventCapture> chaincodeEvents = new Vector<>(); // Test list to capture chaincode events.

		try {

			final String channelName = channel.getName();
			boolean isFooChain = CHANNEL_NAME.equals(channelName);
			System.out.println("Running channel %s"+ channelName);

			Collection<Orderer> orderers = channel.getOrderers();

			Collection<ProposalResponse> responses;
			Collection<ProposalResponse> successful = new LinkedList<>();
			Collection<ProposalResponse> failed = new LinkedList<>();

			// Register a chaincode event listener that will trigger for any chaincode id and only for EXPECTED_EVENT_NAME event.

			String chaincodeEventListenerHandle = channel.registerChaincodeEventListener(Pattern.compile(".*"),
					Pattern.compile(Pattern.quote(EXPECTED_EVENT_NAME)),
					(handle, blockEvent, chaincodeEvent) -> {

						chaincodeEvents.add(new ChaincodeEventCapture(handle, blockEvent, chaincodeEvent));

						String es = blockEvent.getPeer() != null ? blockEvent.getPeer().getName() : blockEvent.getEventHub().getName();
						out("RECEIVED Chaincode event with handle: %s, chaincode Id: %s, chaincode event name: %s, "
										+ "transaction id: %s, event payload: \"%s\", from eventhub: %s",
								handle, chaincodeEvent.getChaincodeId(),
								chaincodeEvent.getEventName(),
								chaincodeEvent.getTxId(),
								new String(chaincodeEvent.getPayload()), es);

					});

			//For non foo channel unregister event listener to test events are not called.
			if (!isFooChain) {
				channel.unregisterChaincodeEventListener(chaincodeEventListenerHandle);
				chaincodeEventListenerHandle = null;

			}

			ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
					.setVersion(CHAIN_CODE_VERSION);
			if (null != CHAIN_CODE_PATH) {
				chaincodeIDBuilder.setPath(CHAIN_CODE_PATH);

			}
			chaincodeID = chaincodeIDBuilder.build();

			if (installChaincode) {
				////////////////////////////
				// Install Proposal Request
				//

				client.setUserContext(sampleOrg.getPeerAdmin());

				out("Creating install proposal");

				InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
				installProposalRequest.setChaincodeID(chaincodeID);

				if (isFooChain) {
					// on foo chain install from directory.

					////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
					System.out.println(" chain code path :: "+Paths.get(TEST_FIXTURES_PATH, CHAIN_CODE_FILEPATH).toFile());
					installProposalRequest.setChaincodeSourceLocation(Paths.get(TEST_FIXTURES_PATH, CHAIN_CODE_FILEPATH).toFile());
					System.out.println("installProposalRequest.getChaincodeSourceLocation() : "+installProposalRequest.getChaincodeSourceLocation());

				} else {
					// On bar chain install from an input stream.

					if (CHAIN_CODE_LANG.equals(Type.GO_LANG)) {

						installProposalRequest.setChaincodeInputStream(Util.generateTarGzInputStream(
								(Paths.get(TEST_FIXTURES_PATH, CHAIN_CODE_FILEPATH, "src", CHAIN_CODE_PATH).toFile()),
								Paths.get("src", CHAIN_CODE_PATH).toString()));
					} else {
						installProposalRequest.setChaincodeInputStream(Util.generateTarGzInputStream(
								(Paths.get(TEST_FIXTURES_PATH, CHAIN_CODE_FILEPATH).toFile()),
								"src"));
					}
				}

				installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);
				installProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);

				out("Sending install proposal");

				////////////////////////////
				// only a client from the same org as the peer can issue an install request
				int numInstallProposal = 0;
				//    Set<String> orgs = orgPeers.keySet();
				//   for (SampleOrg org : testSampleOrgs) {

				Collection<Peer> peers = channel.getPeers();
				numInstallProposal = numInstallProposal + peers.size();
				responses = client.sendInstallProposal(installProposalRequest, peers);

				for (ProposalResponse response : responses) {
					if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
						out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
						successful.add(response);
					} else {
						failed.add(response);
					}
				}

				//   }
				out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

				if (failed.size() > 0) {
					ProposalResponse first = failed.iterator().next();
					System.out.println("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
				}
			}

			//   client.setUserContext(sampleOrg.getUser(TEST_ADMIN_NAME));
			//  final ChaincodeID chaincodeID = firstInstallProposalResponse.getChaincodeID();
			// Note installing chaincode does not require transaction no need to
			// send to Orderers

			///////////////
			//// Instantiate chaincode.
			InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
			instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
			instantiateProposalRequest.setChaincodeID(chaincodeID);
			instantiateProposalRequest.setChaincodeLanguage(CHAIN_CODE_LANG);
			instantiateProposalRequest.setFcn("init");
			instantiateProposalRequest.setArgs(new String[] {
					"100" , "IFCOUS" ,"parminderz_0975@mailinator.com","{\"firstName\":\"Naveen\",\"jobLocation\":\"4.6\"}"});
			Map<String, byte[]> tm = new HashMap<>();
			tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
			tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
			instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
			ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
			chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
			instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

			out("Sending instantiateProposalRequest to all peers with arguments.... ");
			System.out.println("ARGS :: "+instantiateProposalRequest.getArgs());
			successful.clear();
			failed.clear();

			if (isFooChain) {  //Send responses both ways with specifying peers and by using those on the channel.
				responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());
			} else {
				responses = channel.sendInstantiationProposal(instantiateProposalRequest);
			}
			for (ProposalResponse response : responses) {
				if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
					successful.add(response);
					out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				} else {
					failed.add(response);
				}
			}
			out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
			if (failed.size() > 0) {
				for (ProposalResponse fail : failed) {

					out("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + fail.getMessage() + ", on peer" + fail.getPeer());

				}
				ProposalResponse first = failed.iterator().next();
				//System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
			}

			///////////////
			/// Send instantiate transaction to orderer
			out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" + (200 + delta));

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

			channel.sendTransaction(successful, createTransactionOptions() //Basically the default options but shows it's usage.
					.userContext(client.getUserContext()) //could be a different user context. this is the default.
					.shuffleOrders(false) // don't shuffle any orderers the default is true.
					.orderers(channel.getOrderers()) // specify the orderers we want to try this transaction. Fails once all Orderers are tried.
					.nOfEvents(nOfEvents) // The events to signal the completion of the interest in the transaction
			).thenApply(transactionEvent -> {
				try {
					String expect = "" + (300 + delta);
					out("Now query chaincode for the value of b.");
					QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
					queryByChaincodeRequest.setArgs(new String[] {"100"});
					queryByChaincodeRequest.setFcn("query");
					queryByChaincodeRequest.setChaincodeID(chaincodeID);
					System.out.println("PATH :: "+chaincodeID.getPath());

					Map<String, byte[]> tm2 = new HashMap<>();
					tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
					tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
					queryByChaincodeRequest.setTransientMap(tm2);

					Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
					for (ProposalResponse proposalResponse : queryProposals) {
						if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
							/*fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
									". Messages: " + proposalResponse.getMessage()
									+ ". Was verified : " + proposalResponse.isVerified());*/
						} else {
							String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
							out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
							// assertEquals(payload, expect);
						}
					}

					return null;
				} catch (Exception e) {
					out("Caught exception while running query");
					e.printStackTrace();
					//fail("Failed during chaincode query with error : " + e.getMessage());
				}

				return null;

			}).exceptionally(e -> {
				if (e instanceof TransactionEventException) {
					BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
					if (te != null) {
						throw new AssertionError(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()), e);
					}
				}
				e.printStackTrace();
				//throw new AssertionError(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()), e);
				return null;
			}).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);

			// Channel queries

			// We can only send channel queries to peers that are in the same org as the SDK user context
			// Get the peers from the current org being used and pick one randomly to send the queries to.
			//  Set<Peer> peerSet = sampleOrg.getPeers();
			//  Peer queryPeer = peerSet.iterator().next();
			//   out("Using peer %s for channel queries", queryPeer.getName());

			BlockchainInfo channelInfo = channel.queryBlockchainInfo();
			System.out.println("Channel info for : " + channelName);
			System.out.println("Channel height: " + channelInfo.getHeight());
			String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
			String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
			System.out.println("Chain current block hash: " + chainCurrentHash);
			System.out.println("Chainl previous block hash: " + chainPreviousHash);

			// Query by block number. Should return latest block, i.e. block number 2
			channel.queryBlockchainInfo();
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

			// Query block by TxID. Since it's the last TxID, should be block 2
			//returnedBlock = channel.queryBlockByTransactionID(testTxID);
			//out("queryBlockByTxID returned block with blockNumber " + returnedBlock.getBlockNumber());
			// out(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());

			// query transaction by ID
			//TransactionInfo txInfo = channel.queryTransactionByID(testTxID);
			//out("QueryTransactionByID returned TransactionInfo: txID " + txInfo.getTransactionID()
			//        + "\n     validation code " + txInfo.getValidationCode().getNumber());

			if (chaincodeEventListenerHandle != null) {

				channel.unregisterChaincodeEventListener(chaincodeEventListenerHandle);
				//Should be two. One event in chaincode and two notification for each of the two event hubs

				final int numberEventsExpected = channel.getEventHubs().size() +
						channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE)).size();
				//just make sure we get the notifications.
				for (int i = 15; i > 0; --i) {
					if (chaincodeEvents.size() == numberEventsExpected) {
						break;
					} else {
						Thread.sleep(90); // wait for the events.
					}

				}
				//out(numberEventsExpected, chaincodeEvents.size());

				/*for (ChaincodeEventCapture chaincodeEventCapture : chaincodeEvents) {
					out(chaincodeEventListenerHandle, chaincodeEventCapture.handle);
					//out(testTxID, chaincodeEventCapture.chaincodeEvent.getTxId());
					out(EXPECTED_EVENT_NAME, chaincodeEventCapture.chaincodeEvent.getEventName());
					System.out.println(Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEventCapture.chaincodeEvent.getPayload()));
					out(CHAIN_CODE_NAME, chaincodeEventCapture.chaincodeEvent.getChaincodeId());

					BlockEvent blockEvent = chaincodeEventCapture.blockEvent;
					out(channelName, blockEvent.getChannelId());
					//   System.out.println(channel.getEventHubs().contains(blockEvent.getEventHub()));

				}*/

			} else {
				//System.out.println(chaincodeEvents.isEmpty());
			}

			System.out.println("Running for Channel %s done"+ channelName);

		} catch (Exception e) {
			System.out.println("Caught an exception running channel %s"+ channel.getName());
			e.printStackTrace();
			System.out.println("Test failed with error : " + e.getMessage());
		}


		try {
			System.out.println(" init 2 ---   ");
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
			queryByChaincodeRequest.setArgs(  new String []{
					"1" , "IFCOUS" ,"parminderz_0975@mailinator.com","{\"firstName\":\"Naveen\",\"jobLocation\":\"4.6\"}"});
			//instantiateProposalRequest.setArgs(new String[] {
			//					"100" , "IFCOUS" ,"parminderz_0975@mailinator.com","\"firstName\": \"Parminder\",\"middleName\": \"null\", \"lastName\":\"null\""});
			//
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

			out("Sending instantiateProposalRequest to all peers with arguments.... ");
			System.out.println("ARGS :: "+queryByChaincodeRequest.getArgs());


			responses = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());

			for (ProposalResponse response : responses) {
				if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
					successful.add(response);
					out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
				} else {
					failed.add(response);
				}
			}
			out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
			if (failed.size() > 0) {
				for (ProposalResponse fail : failed) {

					out("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + fail.getMessage() + ", on peer" + fail.getPeer());

				}
				ProposalResponse first = failed.iterator().next();
				System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
			}

			///////////////
			/// Send instantiate transaction to orderer
			out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" );

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

			////////////////////////////
			// Send Transaction Transaction to orderer
			System.out.println("successful size : "+successful.size());
			if(successful.size()>0)
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
	}

	static void out(String format, Object... args) {

		System.err.flush();
		System.out.flush();

		System.out.println(format(format, args));
		System.err.flush();
		System.out.flush();

	}
}

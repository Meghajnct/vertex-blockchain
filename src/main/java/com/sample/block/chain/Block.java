package com.sample.block.chain;

import java.util.ArrayList;
import java.util.Date;

import com.sample.block.chain.transaction.Transaction;
import com.sample.block.chain.utils.StringUtils;

public class Block {
	public String hash;
	public String previousHash;
	private String data; //our data will be a simple message.
	private long timeStamp; //as number of milliseconds since 1/1/1970.
	private int nonce;
	public String merkleRoot;
	public ArrayList<Transaction> transactions = new ArrayList<Transaction>(); //our data will be a simple message.


	//Block Constructor.
	public Block(String data,String previousHash ) {
		this.data = data;
		this.previousHash = previousHash;
		this.timeStamp = new Date().getTime();
		this.hash = calculateHash(); //Making sure we do this after we set the other values.
	}
	public Block(String previousHash ) {
		this.previousHash = previousHash;
		this.timeStamp = new Date().getTime();
		
		this.hash = calculateHash(); //Making sure we do this after we set the other values.
	}
	public String calculateHash() {
		String calculatedhash = StringUtils.applySha256( 
				previousHash +
				Long.toString(timeStamp) +
				data 
				);
		return calculatedhash;
	}
	public void mineBlock(int difficulty) {
		merkleRoot = StringUtils.getMerkleRoot(transactions);
		String target = StringUtils.getDificultyString(difficulty); //Create a string with difficulty * "0" 
		while(!hash.substring( 0, difficulty).equals(target)) {
			nonce ++;
			hash = calculateHash();
		}
		System.out.println("Block Mined!!! : " + hash);
	}
	
	public boolean addTransaction(Transaction transaction) {
		//process transaction and check if valid, unless block is genesis block then ignore.
		if(transaction == null) return false;		
		if((previousHash != "0")) {
			if((transaction.processTransaction() != true)) {
				System.out.println("Transaction failed to process. Discarded.");
				return false;
			}
		}
		transactions.add(transaction);
		System.out.println("Transaction Successfully added to Block");
		return true;
	}
	@Override
	public String toString() {
		return "Block [hash=" + hash + ", previousHash=" + previousHash + ", data=" + data + ", timeStamp=" + timeStamp
				+ ", nonce=" + nonce + ", merkleRoot=" + merkleRoot + ", transactions=" + transactions + "]";
	}
	
	/*public void mineBlock(int difficulty) {
		String target = new String(new char[difficulty]).replace('\0', '0'); //Create a string with difficulty * "0" 
		while(!hash.substring( 0, difficulty).equals(target)) {
			nonce ++;
			hash = calculateHash();
		}
		System.out.println("Block Mined!!! : " + hash);
	}*/
}

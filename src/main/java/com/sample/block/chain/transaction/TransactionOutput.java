package com.sample.block.chain.transaction;

import java.security.PublicKey;

import com.sample.block.chain.utils.StringUtils;

public class TransactionOutput {
	
	public String id;
	public PublicKey reciepient; //also known as the new owner of these coins.
	public String value; //the amount of coins they own
	public String parentTransactionId; //the id of the transaction this output was created in
	
	//Constructor
	public TransactionOutput(PublicKey reciepient, String value, String parentTransactionId) {
		this.reciepient = reciepient;
		this.value = value;
		this.parentTransactionId = parentTransactionId;
		this.id = StringUtils.applySha256(StringUtils.getStringFromKey(reciepient)+(value)+parentTransactionId);
	}
	
	//Check if coin belongs to you
	public boolean isMine(PublicKey publicKey) {
		return (publicKey == reciepient);
	}

}

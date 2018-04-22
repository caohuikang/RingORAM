package com.ringoram;

import java.nio.ByteBuffer;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

public class MessageUtility {
	
	public static final int ORAM_INIT = 1;//initialize server
	public static final int ORAM_GETMETA = 2;//get bucket meta data
	public static final int ORAM_READBUCKET = 3;//read bucket
	public static final int ORAM_WRITEBUCKET = 4;//write bucket
	public static final int ORAM_READBLOCK = 5;//read block
	
	   public static ByteBuffer createTypeReceiveBuffer() {
	        // 4 for type
	        // 4 for size
	        return ByteBuffer.allocate(4 + 4);
	    }
	   
	public static int[] parseTypeAndLength(ByteBuffer b) {
	        int[] typeAndLength = new int[2];

	        byte[] messageTypeBytes = new byte[4];
	        byte[] messageLengthBytes = new byte[4];

	        b.get(messageTypeBytes);
	        int messageType = Ints.fromByteArray(messageTypeBytes);

	        b.get(messageLengthBytes);
	        int messageLength = Ints.fromByteArray(messageLengthBytes);

	        typeAndLength[0] = messageType;
	        typeAndLength[1] = messageLength;

	        return typeAndLength;
	    }
	 
	public static byte[] createMessageHeaderBytes(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return Bytes.concat(messageTypeBytes, messageLengthBytes);
    }
	
	public static ByteBuffer createMessageHeaderBuffer(int messageType, int messageSize) {
        byte[] messageTypeBytes = Ints.toByteArray(messageType);
        byte[] messageLengthBytes = Ints.toByteArray(messageSize);

        return ByteBuffer.wrap(Bytes.concat(messageTypeBytes, messageLengthBytes));
    }
	
}


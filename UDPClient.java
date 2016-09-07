import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class UDPClient {
	public static final String HOST = "dns.postel.org";
	public static final int PORT = 60450;
//	public static final String HOST = "127.0.0.1";
//	public static final int PORT = 9790;
	public static final int BUFFER_SIZE = 500;

	public static void main(String[] args) {
		String s = null;
		Integer p = null;
		for(int count = 0; count < args.length; count++) {
			if (!args[count].startsWith("-")) {
				break;
			}
			if (args[count].equals("-s")) {
				s = args[++count];
			} else if (args[count].equals("-p")) {
				try {
					p = Integer.parseInt(args[++count]);
				} catch (Exception e) {}
			}
		}
		
		if (s == null || p == null) {
			System.out.println("No arguments or arguments incorrect. Using default server, port.");
			s = HOST; 
			p = PORT;
		}
		
		UDPClient c = new UDPClient(s, p);
		c.run();
	}

	/**
	 * Variables
	 */
	private DatagramSocket client;
	private String serverAddr;
	private int serverPort;
	private String myNumber;
	//HashMap to store outgoing messages, key is message number, value is message; 
	private HashMap<String, Message> messages; 
	//HashMap to store registry map from step2
	private HashMap<String, Node> nodeMap;
	
	public UDPClient(String host, int port) {
		this.serverAddr = host;
		this.serverPort = port;
		messages = new HashMap<String, Message>();
		nodeMap = new HashMap<String, Node>();
				
		try {
			client = new DatagramSocket();
//			System.out.println("Client port: " + client.getLocalPort());
		} catch (Exception e) {

		}
	}

	/**
	 * Entry point
	 */
	public void run() {
		//1. register first;
		try {
			register();
		} catch (Exception e) {
			System.out.println("Register error, please re-execute this program.");
			return;
		}
		
		System.out.println("\nPlease enter command: ");
		
		//2. if registered, start to take user's input/listening incoming message;
		InputStream ins = System.in;
		BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
		while (true) {
			try {
				if (ins.available() > 0) {
					String line = reader.readLine();
					
					if ("REG".equals(line.toUpperCase())) {
						pullMap();
					} else if (line.toUpperCase().startsWith("UNI") ) {
		        		if (!line.endsWith(".")) {
		        			System.out.println("Your command is incorrect. Please verify. It should be "
		        					+ "UNI <NUM> <String>, string should end with '.'");
		        			continue;
		        		}
		        		
		        		line = line.substring(3, line.length());
		        		
		        		int index = 0;
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        			if (index == line.length()) {
		        				System.out.println("Your command is incorrect. Please verify. It should be "
			        					+ "UNI <NUM> <String>, string should not be empty.");
		        				continue;
		        			}
		        		}
		        		
		        		line = line.substring(index, line.length());
		        		
		        		index = 0;
		        		while (Character.isDigit(line.charAt(index))) {
		        			index++;
		        		}
		        		if (index != 2) {
		        			System.out.println("Your command is incorrect. Please verify. "
		        					+ "<NUM> should be a 2 digit number");
	        				continue;
		        		}
		        		String sendTo = line.substring(0, index);
		        		if  (nodeMap.get(sendTo) == null && !"99".equals(sendTo)) {
		        			System.out.println(sendTo + " not exist in map. Please verify or type REG to pull latest map.");
		        			continue;
		        		}
		        		
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        			if (index == line.length()) {
		        				System.out.println("Your command is incorrect. Please verify. It should be "
			        					+ "UNI <NUM> <String>, string should not be empty.");
		        				continue;
		        			}
		        		}
		        		
		        		String message = line.substring(index, line.length());
		    
		        		sendMessageTo(sendTo, message);
		        	} else if (line.toUpperCase().startsWith("BRD"))  {
		        		if (!line.endsWith(".")) {
		        			System.out.println("Your command is incorrect. Please verify. It should be "
		        					+ "BRD <String>, string should end with '.'");
		        			continue;
		        		}
		        		
		        		line = line.substring(3, line.length());
		        		
		        		int index = 0;
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        			if (index == line.length()) {
		        				System.out.println("Your command is incorrect. Please verify. It should be "
			        					+ "BRD <String>, string should not be empty.");
		        				continue;
		        			}
		        		}
		        		
		        		if (nodeMap.size() < 2) {
		        			System.out.println("There is no enough node in registry map, "
		        					+ "please use REG to pull the map first.");
		        			continue;
		        		}
		        		
		        		if (nodeMap.size() == 2 && nodeMap.get("01") != null 
		        				&& nodeMap.get(myNumber) != null) {
		        			System.out.println("There is no node other than nameserver and yourself "
		        					+ "please use REG to pull latest map or wait.");
		        			continue;
		        		}
		        		
		        		String message = line.substring(index, line.length());
		        		broadcast(message);
		        	} else if (line.toUpperCase().startsWith("FWD") && line.toUpperCase().contains("VIA")) {
		        		if (!line.endsWith(".")) {
		        			System.out.println("Your command is incorrect. Please verify. "
		        					+ "String should end with '.'");
		        			continue;
		        		}
		        		
		        		line = line.substring(3, line.length());//__<num1> VIA <num2> string
		        		int index = 0;
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        			if (index == line.length()) {
		        				System.out.println("Your command is incorrect. Please verify. It should be "
			        					+ "FWD <NUM> VIA <NUM> <String>, string should not be empty.");
		        				continue;
		        			}
		        		}
		        		
		        		line = line.substring(index, line.length()); //<num1> VIA <num2> string
		        		index = 0;
		        		while (Character.isDigit(line.charAt(index))) {
		        			index++;
		        		}
		        		if (index != 2) {
		        			System.out.println("Your command is incorrect. Please verify. "
		        					+ "<NUM> should be a 2 digit number");
	        				continue;
		        		}
		        		String fwd = line.substring(0, index); //<num1>
		        		
		        		index = line.toUpperCase().indexOf("VIA");
		        		line = line.substring(index+3, line.length()); // _<num2>_<string>
		        		index = 0;
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        		}
		        		
		        		line = line.substring(index, line.length()); //<num2>_<string>
		        		index = 0;
		        		while (Character.isDigit(line.charAt(index))) {
		        			index++;
		        		}
		        		if (index != 2) {
		        			System.out.println("Your command is incorrect. Please verify. "
		        					+ "<NUM> should be a 2 digit number");
	        				continue;
		        		}
		        		String via = line.substring(0, index); //<num2>
		        		if  (nodeMap.get(via) == null) {
		        			System.out.println(via + " not exist in map. Please verify or type REG to pull latest map.");
		        			continue;
		        		}
		        		
		        		line = line.substring(index, line.length()); //__<String>.
		        		index = 0;
		        		while (line.charAt(index) == ' ' || line.charAt(index) == '\t') {
		        			index++;
		        		}
		        		
		        		String message = line.substring(index, line.length());
		        		forward(fwd, via, message);
		        	} else {
						System.out.println("Your command is invalid, please retype.");
						continue;
					}
					System.out.println("\nPlease enter command: ");
					
				} else {
					//user didn't input
					byte buffer[] = new byte[BUFFER_SIZE];
					DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
					client.setSoTimeout(500);
					client.receive(pack);
					String received = new String(pack.getData(), 0, pack.getLength());
					System.out.println("\tRecv: " + getPacketString(pack));
					Message recvMsg = new Message(received);
					
					if (!recvMsg.isValid) {
						System.out.println("\tReceived message format is invalid");
						System.out.println("\nPlease enter command: ");
						continue;
					}
					//step 3, receive other people's msg, return OK
					if (recvMsg.np == Protocol.DATA) {
						sendDataConfirm(recvMsg, pack);
					} else if (recvMsg.np == Protocol.BROADCAST) {
						sendDataConfirm(recvMsg, pack);
					} else {
						System.out.println("\tReceived message dosen't match our protocol. Not proceed.");
					}
					
					System.out.println("\nPlease enter command: ");
				}
			} catch (SocketTimeoutException ex) {
				continue;
			} catch (Exception e) {
				System.out.println("Unexpected error: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Send message, return received message as string
	 */
	private String send(Message message, String address, int port) {
		String retVal = null;
		try {
			client.setSoTimeout(2000);

			int attempt = 1;
			
			while (attempt <= 5) {
				byte buffer[] = new byte[BUFFER_SIZE];
				try {
					byte[] sendData = message.toString().getBytes();
					InetAddress IPAddress = InetAddress.getByName(address);
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
					if (attempt == 1) {
						System.out.println("\tSend: " + getPacketString(sendPacket));
					}
					client.send(sendPacket);
					
					DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
					client.receive(pack);
					
					String received = new String(pack.getData(), 0, pack.getLength());
					System.out.println("\tRecv: " + getPacketString(pack));
					
					Message recvMsg = new Message(received);
					if (!recvMsg.isValid) {
						throw new MessageInvalidException();
					}
					
					if (!recvMsg.no.equals(message.no)) {
						throw new MessageInvalidException();
					}
					
					if (!protocolMatch(message, recvMsg)) {
						throw new MessageInvalidException();
					}
					
					retVal = received;
					break;
				} catch (SocketTimeoutException e) {
					System.out.println("\t\tAttempt: " + attempt + " time out.");
					attempt++;
				} catch (Exception e) {
					System.out.println("\t\tAttempt: " + attempt + ", received invalid message.");
					attempt++;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return retVal;
	}
	
	/**
	 * Send message, 
	 */
	private boolean sendConfirm(Message message, String address, int port, boolean fwd) {
		boolean success = false;
		try {
			byte[] sendData = message.toString().getBytes();
			InetAddress IPAddress = InetAddress.getByName(address);
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
			System.out.println("\tSend: " + getPacketString(sendPacket));
			client.send(sendPacket);
			
			if (fwd) {
				byte buffer[] = new byte[BUFFER_SIZE];
				DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
				client.receive(pack);
				System.out.println("\tRecv: " + getPacketString(pack));
			}
			success = true;
		} catch (Exception e) {
			success = false;
		}
		return success;
	}
	
	/**
	 * Step 1: Register
	 */
	private void register() throws Exception {
		System.out.println("Registering...");
		String msgNum = getNextMsgNum();
		Message reqMsg = new Message("00", "01", Protocol.REGISTRATION_REQ, 1, msgNum, "", "register me");
		messages.put(msgNum, reqMsg);
		
		String retVal = send(reqMsg, serverAddr, serverPort);
		
		//if null, register fail
		if (retVal == null) {
			throw new RuntimeException();
		}
		retVal = retVal.trim();
		int index = retVal.length() -1;
		while (retVal.charAt(index) != ' ' && retVal.charAt(index) != '\t') {
			index--;
		}
		
		myNumber = retVal.substring(index+1, retVal.length());
		
		if (myNumber.length() != 2) {
			throw new RuntimeException();
		}
		
		for (int i = 0; i < myNumber.length(); i++) {
			if (!Character.isDigit(myNumber.charAt(i))) {
				throw new RuntimeException();
			}
		}
		
		System.out.println("Register successfully as: " + myNumber);
	}
	

	/**
	 * Step 2: Pull registry map
	 */
	private void pullMap()  {
		System.out.println("Pull registry map");
		nodeMap = new HashMap<String, Node>();
		String msgNum = getNextMsgNum();
		Message reqMsg = new Message(myNumber, "01", Protocol.REGISTRY_DUMP_REQ , 1, msgNum, "", "GET MAP");
		messages.put(msgNum, reqMsg);
		
		String retVal = send(reqMsg, serverAddr, serverPort);
		if (retVal == null) {
			System.out.println("Pull registry map error, please retry.");
			return;
		}
		Message respMsg = new Message(retVal);
		createNodeMap(respMsg.data);	
	}
	
	/**
	 * Step 3, send message to someone
	 */
	private void sendMessageTo(String sendto, String message) {
		String msgNum = getNextMsgNum();
		if ("99".equals(sendto)) {
			System.out.println("Sending message to ALL..");
			Message reqMsg = new Message(myNumber, "99", Protocol.DATA , 1, msgNum, "", message);
			messages.put(msgNum, reqMsg);
			for (String key : nodeMap.keySet()) {
				if (key.equals(myNumber)) continue;
				Node node = nodeMap.get(key);
				
				String retVal = send(reqMsg, node.ip, node.port);
				if (retVal == null) {
					System.out.println("\tSend Message to " + node.name + " error \n");
				}
			}
		} else {
			Node node = nodeMap.get(sendto);
			
			Message reqMsg = new Message(myNumber, sendto, Protocol.DATA , 1, msgNum, "", message);
			messages.put(msgNum, reqMsg);
			
			String retVal = send(reqMsg, node.ip, node.port);
			if (retVal == null) {
				System.out.println("\tSend Message to " + sendto + " error , please retry.");
			}
		}
	}
	
	/**
	 * Step 3, 5, send confirm to others
	 */
	private void sendDataConfirm(Message recvMsg, DatagramPacket pack) {
		Message send = null;
		if (recvMsg.np == Protocol.BROADCAST) {
			send = new Message(recvMsg.to, recvMsg.fm, Protocol.BROADCAST_CONFIRM, 
					1, recvMsg.no, "", "OK");
		} else if (recvMsg.np == Protocol.DATA) {
			send = new Message(recvMsg.to, recvMsg.fm, Protocol.DATA_CONFIRM, 
					1, recvMsg.no, "", "OK");
		} 
		
		if (send == null) {
			return;
		}
		
		boolean success = sendConfirm(send, pack.getAddress().getHostAddress(), pack.getPort(), false);
		
		if (!success) {
			System.out.println("\tException during sending confirmation.");
		}
		
		if ("99".equals(recvMsg.to) || recvMsg.np == Protocol.BROADCAST) {
			return;
		}
		
		//If myself is not final destination
		if (!myNumber.equals(recvMsg.to)) {
			System.out.println("Relay message...");
			if (recvMsg.hc == 0) {
				System.out.println("dropped - hopcount exceeded.");
			} else if (recvMsg.hc > 0) {
				if (isInVLlist(myNumber, recvMsg)) {
					System.out.println("dropped - node revisited. VL: " + recvMsg.vlString);
				} else {
					send = new Message(recvMsg.fm, recvMsg.to, Protocol.DATA, 
							recvMsg.hc - 1, recvMsg.no, recvMsg.getVLString() + "," + myNumber, recvMsg.data);
					int count = nodeMap.size();
					if (count < 2) {
						System.out.println("No enough node to fwd message. Please use REG pull latest map.");
						return;
					}
					if (count == 2 && nodeMap.get("01") != null && nodeMap.get(myNumber) != null) {
						System.out.println("No enough node to fwd message. Please use REG pull latest map.");
						return;
					}
					
					ArrayList<Node> randomNodes = getRandomNodes();
					for (Node node : randomNodes) {
						//foward messge to 5 random nodes. no need to receive .
						boolean fwdSuccess = sendConfirm(send, node.ip, node.port, true);
						if (!fwdSuccess) {
							System.out.println("\tSend Message to " + node.name + " error.");
						}
					}
				}
			}
		} 
		
	}
	
	/**
	 * Step 4, broadcast
	 */
	private void broadcast(String message) {
		String msgNum = getNextMsgNum();
		Message reqMsg = new Message(myNumber, "99", Protocol.BROADCAST , 1, msgNum, "", message);
		messages.put(msgNum, reqMsg);
		
		for (String key : nodeMap.keySet()) {
			if (key.equals("01") || key.equals(myNumber)) {
				continue;
			}
			Node node = nodeMap.get(key);

			String retVal = send(reqMsg, node.ip, node.port);
			if (retVal == null) {
				System.out.println("\tSend Message to " + key + " error.");
			}
		}
	}
	
	/**
	 * Step 5 forward msg
	 */
	private void forward(String fwd, String via, String message) {
		Node node = nodeMap.get(via);
		String msgNum = getNextMsgNum();
		Message reqMsg = new Message(myNumber, fwd, Protocol.DATA , 9, msgNum, myNumber, message);
		messages.put(msgNum, reqMsg);
		
		boolean success = sendConfirm(reqMsg, node.ip, node.port, true);
		if (!success) {
			System.out.println("\tSend Message to " + via + " error , please retry.");
			return;
		}
	}
	
	/**
	 * Helper, generate "NO" number for message;
	 */
	private String getNextMsgNum() {
		int num = messages.size();
		num++;
		if (num < 10) {
			return "00" + num;
		} else if (num < 100) {
			return "0" + num;
		} else {
			return "" + num;
		}
	}
	
	/**
	 * Helper, generete a packet to string for log
	 */
	private String getPacketString(DatagramPacket p) {
		String str = "[" + p.getAddress() + ":" + p.getPort() + "] ";
		str += new String(p.getData());
		return str;
	}
	
	/**
	 * Helper for step 2
	 * @param data
	 */
	private void createNodeMap(String data) {
		//DATA:01=128.9.112.1@60450,03=23.243.18.253@53040,04=23.243.18.253@56508,05=23.243.18.253@49681,06=24.7.32.24@52224
		System.out.println("\tThe registry map is:");
		String[] nodes = data.split(",");
		for (int i = 0; i < nodes.length; i++) {
			try {
				String nodeStr = nodes[i];
				String[] split = nodeStr.split("=");
				String nodeNum = split[0];
				
				String ipandPort = split[1];
				split = ipandPort.split("@");
				String ip = split[0];
				if (!validIP(ip)) {
					continue;
				}
				
				String port = split[1];
				
				Node node = new Node(nodeNum, ip, Integer.valueOf(port));
				nodeMap.put(nodeNum, node);
				System.out.println("\t" + node.toString());
			} catch (Exception e) {
				continue;
			}
		}	
	}
	
	private static boolean validIP (String ip) {
	    try {
	        if ( ip == null || ip.isEmpty() ) {
	            return false;
	        }

	        String[] parts = ip.split( "\\." );
	        if ( parts.length != 4 ) {
	            return false;
	        }

	        for ( String s : parts ) {
	            int i = Integer.parseInt( s );
	            if ( (i < 0) || (i > 255) ) {
	                return false;
	            }
	        }
	        if ( ip.endsWith(".") ) {
	            return false;
	        }

	        return true;
	    } catch (Exception nfe) {
	        return false;
	    }
	}
	
	/**
	 * Helepr, check if my number is already exist in VL list
	 */
	private boolean isInVLlist(String myNumber, Message msg) {
		ArrayList<String> list = msg.vl;
		if (list.contains(myNumber)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Helper, check if send and receive message protocol match
	 */
	private boolean protocolMatch(Message send, Message recv) {
		if (send.np == Protocol.REGISTRATION_REQ && recv.np == Protocol.REGISTRATION_RESP) {
			if (send.fm.equals(recv.to) && (send.to.equals(recv.fm))) {
				return true;
			}
		}
		if (send.np == Protocol.REGISTRY_DUMP_REQ && recv.np == Protocol.REGISTRY_DUMP_RESP) {
			return true;
		}
		if (send.np == Protocol.DATA && recv.np == Protocol.DATA_CONFIRM) { 
			if (send.fm.equals(recv.to) && (send.to.equals(recv.fm)) && "OK".equals(recv.data.trim())) {
				return true;
			}
		}
		if (send.np == Protocol.BROADCAST && recv.np == Protocol.BROADCAST_CONFIRM) {
			if (send.fm.equals(recv.to) && (send.to.equals(recv.fm)) && "OK".equals(recv.data.trim())) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Helper, generate 5 random nodes for fwd message
	 */
	private ArrayList<Node> getRandomNodes() {
		ArrayList<Node> list = new ArrayList<Node>();
		StringBuilder sb = new StringBuilder();
		if (nodeMap.size() <= 7) {
			for (String key : nodeMap.keySet()) {
				if (!key.equals("01") && !key.equals(myNumber)) {
					list.add(nodeMap.get(key));
					sb.append(nodeMap.get(key).name + ",");
				}
			}
		} else {
			String[] keys = nodeMap.keySet().toArray(new String[nodeMap.size()]);
			int length = keys.length;
			Random r = new Random();
			while (list.size() < 5) {
				int index = r.nextInt(length);
				Node node = nodeMap.get(keys[index]);
				if (!list.contains(node)) {
					list.add(node);
					sb.append(node.name + ",");
				}
			}
		}
		System.out.println("\tRandom nodes are: " + sb.toString());
		return list;
	}
}

class Protocol {
	public static final int REGISTRATION_REQ = 1;
	public static final int REGISTRATION_RESP = 2;
	public static final int DATA = 3;
	public static final int DATA_CONFIRM = 4;
	public static final int REGISTRY_DUMP_REQ = 5;
	public static final int REGISTRY_DUMP_RESP = 6;
	public static final int BROADCAST = 8;
	public static final int BROADCAST_CONFIRM = 9;
}


class MessageInvalidException extends Exception {
	
}


class Message {
	String fm;
	String to;
	int np;
	int hc;
	String no;
	ArrayList<String> vl;
	String vlString;
	String data;
	boolean isValid;
	HashMap<String, String> dataMap;
	Message response;
	
	public Message(String message) {
		if (!message.contains("DATA") || !message.contains("FM") || !message.contains("TO") || !message.contains("NP")
				|| !message.contains("HC") || !message.contains("NO") || !message.contains("VL")) {
			isValid = false;
			return;
		}
		
		message = removeExtraSpace(message);
		
		String[] msgs = message.split(" ");
		if (msgs.length < 7) {
			isValid = false;
			return;
		}
		try {
			dataMap = new HashMap<String, String>();
			int i;
			for (i = 0; i < 6; i++) {
				String current = msgs[i];
				String[] split = current.split(":");
				String prefix = split[0];
				String suffix = null;
				if (split.length == 2) {
					suffix = split[1];
				}
	//			System.out.println(prefix + "  " + suffix);
				dataMap.put(prefix, suffix);
			} 
			StringBuilder dataSb = new StringBuilder();
			for (; i < msgs.length - 1; i++) {
				dataSb.append(msgs[i]).append(" ");
			}
			dataSb.append(msgs[msgs.length-1]);
			
			String[] dataStr = dataSb.toString().split(":");
			String suffix = null;
			if (dataStr.length == 2) {
				suffix = dataStr[1];
			}
			dataMap.put("DATA", suffix);
		
			fm = dataMap.get("FM");
			to = dataMap.get("TO");
			np = Integer.parseInt(dataMap.get("NP"));
			hc = Integer.parseInt(dataMap.get("HC"));
			no = dataMap.get("NO");
			vlString = dataMap.get("VL") == null? "":dataMap.get("VL");
			vl = getVLFromStr(dataMap.get("VL"));
			data = dataMap.get("DATA");
			
			if (fm.length() != 2 || to.length() != 2 || np > 10 || hc > 10 || no.length() != 3) {
				isValid = false;
				return;
			}
			
			if (!checkDigit(fm) || !checkDigit(to) || !checkDigit(no)) {
				isValid = false;
				return;
			}
			
		} catch (Exception e) {
			System.out.println("The incoming message format is invalid: " + message);
			isValid = false;
			return;
		}
		isValid = true;
		
	}
	
	public Message(String fm, String to, int np, int hc, String no, String vl, String data) {
		this.fm = fm;
		this.to = to;
		this.np = np;
		this.hc = hc;
		this.no = no;
		this.data = data;
		isValid = true;
		this.vl = getVLFromStr(vl);
		vlString = vl;
		response = null;
	}
	
	public ArrayList<String> getVLFromStr(String vl) {
		ArrayList<String> list = new ArrayList<String>();
		if (vl != null && vl.length() > 0) {
			String[] split = vl.split(",");
			for (int i = 0; i < split.length; i++) {
				if (split[i].equals("")) continue;
				list.add(split[i]);
			}
		}
		return list;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("FM:").append(fm).append(" ");
		sb.append("TO:").append(to).append(" ");
		sb.append("NP:").append(np).append(" ");
		sb.append("HC:").append(hc).append(" ");
		sb.append("NO:").append(no).append(" ");
		sb.append("VL:").append(getVLString()).append(" ");
		sb.append("DATA:").append(data);
		return sb.toString();
	}
	
	public String getVLString() {
		return vlString;
	}
	
	private String removeExtraSpace(String str) {
		StringBuilder sb = new StringBuilder();
		
		int index = 0;
		while (index < str.indexOf("DATA")) {
			if (str.charAt(index) == ' ' || str.charAt(index) == '\t') {
				if (index > 0) {
					if (str.charAt(index-1) != ' ' && str.charAt(index-1) != '\t') {
						sb.append(str.charAt(index));
					}
				} 
			} else {
				sb.append(str.charAt(index));
			}
			index++;
		}
		
		while (index < str.length()) {
			sb.append(str.charAt(index));
			index++;
		}
		
		return sb.toString();
	}
	
	private boolean checkDigit(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}


/**
 * Represent a node in registry table
 * in step 2
 *
 */
class Node {
	String name;
	String ip;
	int port;
	
	public Node(String name, String ip, int port) {
		this.name = name;
		this.ip = ip;
		this.port = port;
	}
	
	public String toString() {
		return name + " = " + ip + ":" + port;
	}
}



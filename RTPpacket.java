

import java.util.Arrays;

/*
 *  0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|X|  CC   |M|     PT      |       sequence number         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                           timestamp                           |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |           synchronization source (SSRC) identifier            |
   +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
   |            contributing source (CSRC) identifiers             |
   |                             ....                              |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * */
public class RTPpacket {
	static int HEADER_SIZE = 12; 
	private int Version; 
	private int Padding; 
	private int Extension; 
	private int CC; 
	private int Marker; 
	private int PayloadType; 
	private int SeqNum; 
	private int TimeStamp; 
	private int Ssrc; 
	
	//bitstream of the RTP header 
	private byte[] header;
	// size of RTP payload 
	private int payload_size; 
	//bitstream of the RTP payload 
	public byte[] payload; 
	
	/*
	 * Constructor RTP packet from header fileds and payload bitstream 
	 * */
	public RTPpacket(int PType, int Framenb, int Time, byte[]data, int data_length) {
		// TODO Auto-generated constructor stub
		Version = 2; 
		Padding = 0; 
		Extension = 0; 
		CC = 0; 
		Marker = 0; 
		Ssrc = 1337; //random 
		//fill changing header fields 
		SeqNum = Framenb; 
		TimeStamp = Time; 
		PayloadType = PType; 
		header = new byte[HEADER_SIZE]; 
		
		//fill the header array of byte with RTP header fields 
		header[0] = (byte)(Version << 6 | Padding << 5 | Extension << 4 | CC); 
		header[1] = (byte)(Marker << 7 | PayloadType & 0x000000FF); 
		header[2] = (byte)(SeqNum >> 8); 
		header[3] = (byte)(SeqNum & 0xFF); 
		header[4] = (byte)(TimeStamp >> 24); 
		header[5] = (byte)(TimeStamp >> 16); 
		header[6] = (byte)(TimeStamp >> 8); 
		header[7] = (byte)(TimeStamp & 0xFF); 
		header[8] = (byte)(Ssrc >> 24); 
		header[9] = (byte)(Ssrc >> 16); 
		header[10] = (byte)(Ssrc >> 8); 
		header[11] = (byte)(Ssrc & 0xFF);
		
		//fill the payload bitstream 
		payload_size = data_length; 
		payload = new byte[payload_size]; 
		payload = Arrays.copyOf(data, payload_size);
	}
	
	/*
	 * Constructor RTP packet from packet bitstream 
	 */
	
	public RTPpacket(byte[]packet, int packet_size) {
		//default fields
		Version = 2; 
		Padding = 0;
		Extension = 0; 
		CC = 0; 
		Marker = 0; 
		Ssrc = 0; 
		
		//check if total packet size is lower than the header
		if (packet_size >= HEADER_SIZE) {
			header = new byte[HEADER_SIZE]; 
			
			for (int i = 0; i < HEADER_SIZE; i++) {
				header[i] = packet[i]; 
			}
			
			//get payload bitstream 
			payload_size = packet_size - HEADER_SIZE; 
			payload = new byte[payload_size]; 
			for (int i = HEADER_SIZE; i < packet_size; i++) {
				payload[i - HEADER_SIZE] = packet[i]; 
			}
			
			//Interpret the changing fieldes of the header 
			Version = (header[0] & 0xFF) >>> 6;
			PayloadType = (header[1] & 0x7F); 
			SeqNum = (header[3] & 0xFF) + ((header[2] & 0xFF) << 8);
			TimeStamp = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);
		}
	}
	
	public int getPayload(byte[]data) {
		for (int i = 0; i < payload_size; i++) {
			data[i] = payload[i]; 
		}
		return payload_size; 
	}
	
	public int getPayload_length() {
		return payload_size; 
	}
	
	public int getPacket(byte[] packet) {
		for (int i = 0; i < HEADER_SIZE; i++) {
			packet[i] = header[i]; 
		}
		for (int i = 0; i < payload_size; i++) {
			packet[i + HEADER_SIZE] = payload[i]; 
		}
		return (HEADER_SIZE + payload_size); 
	}
	public int get_length() {
		return (HEADER_SIZE + payload_size); 
	}
	
	public int getTimestamp() {
		return TimeStamp; 
	}
	
	public int getSequenceNumber() {
		return SeqNum; 
	}
	
	public int getPayloadType() {
		return PayloadType; 
	}
	
	public void printHeader() {
		System.out.println("[RTP-Header]"); 
        System.out.println("Version: " + Version 
                + ", Padding: " + Padding
                + ", Extension: " + Extension 
                + ", CC: " + CC
                + ", Marker: " + Marker 
                + ", PayloadType: " + PayloadType
                + ", SequenceNumber: " + SeqNum
                + ", TimeStamp: " + TimeStamp);
	}

}

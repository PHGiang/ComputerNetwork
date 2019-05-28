import java.nio.ByteBuffer;

public class RTCPpacket {
	//Receiver Report RTCP packet 
/*
 *         0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    RC   |   PT=RR=201   |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                     SSRC of packet sender                     |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_1 (SSRC of first source)                 |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  1    | fraction lost |       cumulative number of packets lost       |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           extended highest sequence number received           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      interarrival jitter                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         last SR (LSR)                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                   delay since last SR (DLSR)                  |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_2 (SSRC of second source)                |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  2    :                               ...                             :
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |                  profile-specific extensions                  |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * 
 */
	final static int HEADER_SIZE = 8;
	final static int BODY_SIZE = 24; 
	
	private int Version; 
	private int Padding; 
	private int RC;
	private int PayloadType; 
	private int length; 
	private int Ssrc;
	private int cumLost; 
	private int highSeqNb; 
	public float fractionLost; 
	private byte[]header; 
	private byte[]body; 
	
	public RTCPpacket(float fractionLost, int cumLost, int highSeqNb) {
		// TODO Auto-generated constructor stub
		//fill the constant fields
		Version = 2; 
		Padding = 0; 
		RC = 1; 
		PayloadType = 201; 
		length = 32; 
		//fill changed fields 
		this.fractionLost = fractionLost; 
		this.cumLost = cumLost; 
		this.highSeqNb = highSeqNb; 
		header = new byte[HEADER_SIZE]; 
		body = new byte[BODY_SIZE]; 
		
		header[0] = (byte)(Version << 6 | Padding << 5 | RC); 
		header[1] = (byte)(PayloadType & 0xFF); 
		header[2] = (byte)(length >> 8); 
		header[3] = (byte)(length & 0xFF); 
		header[4] = (byte)(Ssrc >> 24); 
		header[5] = (byte)(Ssrc >> 16); 
		header[6] = (byte)(Ssrc >> 8); 
		header[7] = (byte)(Ssrc & 0xFF); 
		
		ByteBuffer bb = ByteBuffer.wrap(body); 
		bb.putFloat(fractionLost); 
		bb.putInt(cumLost); 
		bb.putInt(highSeqNb); 
	}
	
	public RTCPpacket(byte[]packet, int packet_size) {
		header = new byte[HEADER_SIZE]; 
		body = new byte[BODY_SIZE];
		System.arraycopy(packet, 0, header, 0, HEADER_SIZE);
		System.arraycopy(packet, HEADER_SIZE, body, 0, BODY_SIZE);
		
		//Parse header field
		Version = (header[0] & 0xFF) >> 6; 
		PayloadType = header[1] & 0xFF; 
		length = (header[3] & 0xFF) + ((header[2] & 0xFF) << 8); 
		Ssrc = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);
		
		//Parse body field 
		ByteBuffer bb = ByteBuffer.wrap(body); 
		fractionLost = bb.getFloat(); 
		cumLost = bb.getInt(); 
		highSeqNb = bb.getInt(); 
	}
	
	//Return packet bitstream and its length 
	
	public int getPacket(byte[]packet) {
		System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
		System.arraycopy(body, 0, packet, HEADER_SIZE, BODY_SIZE);
		return (BODY_SIZE + HEADER_SIZE); 
	}
	
	public int getLength() {
		return (BODY_SIZE + HEADER_SIZE); 
	}
	
	public String toString() {
		return ("[RTCP] Version: " + Version + ", Fraction Lost: " + fractionLost +
				", Cummulative Lost: " + cumLost + ", Highest Seq Num: " + highSeqNb); 
	}

}

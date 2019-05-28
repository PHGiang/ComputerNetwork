

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

public class Server extends JFrame implements ActionListener{
	//RTP variables 
	DatagramSocket RTPsocket; 
	DatagramPacket senddp;
	
	InetAddress clientIPAddr; 
	int RTP_dest_port = 0; 
	int RTSP_dest_port = 0;
	
	//GUI
	JLabel label;
	
	//Video variables 
	int imagenb = 0;
	VideoStream video;
	static int MJPEG_TYPE = 26; 
	static int FRAME_PERIOD = 100; 
	static int VIDEO_LENGTH = 500; //length of video in frames
	
	//Requested video from client  
	static String videoFileName; 
	
	Timer timer; 
	byte[]buf; 
	int sendDelay; 
	
	//RTSP variables
	//RTSP states 
	final static int INIT = 0; 
	final static int READY = 1; 
	final static int PLAYING = 2;
	
	//rtsp message types 
	final static int SETUP = 3; 
	final static int PLAY = 4; 
	final static int PAUSE = 5; 
	final static int TEARDOWN = 6; 
	
	//RTSP state variable
	static int state; 
	
	//Socket to send/received RTSP messages
	Socket RTSPsocket; 
	
	//input and output stream for RTSP messages
	static BufferedReader RTSPBufferedReader; 
	static BufferedWriter RTSPBufferedWriter; 
	
	static String RTSPid = UUID.randomUUID().toString(); 
	int RTSPSeqNb = 0; 
	
	//RTCP variables 
	static int RTCP_RCV_PORT = 19001; 
	static int RTCP_PERIOD = 400; 
	DatagramSocket RTCPsocket; 
	RtcpReceiver rtcpReceiver; 
	//Performance optimization and congestion controller
	int congestionLevel; 
	CongestionController cc; 
	ImageTranslator imgTranslator; 
	
	
	final static String CRLF = "\r\n"; 
	
	public Server() {
		super("RTSP Server"); 
		
		//init RTP sending Timer 
		sendDelay = FRAME_PERIOD; 
		timer = new Timer(sendDelay, this); 
		timer.setInitialDelay(0);
		timer.setCoalesce(true);
		
		cc = new CongestionController(600); 
		buf = new byte[20000]; 
		
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				timer.stop();
				rtcpReceiver.stopRcv();
				System.exit(0);
			}
		});
		
		//Init RTCP packet receiver
		rtcpReceiver = new RtcpReceiver(RTCP_PERIOD); 
		
		//GUI 
		label = new JLabel("Send frame #        ", JLabel.CENTER); 
		getContentPane().add(label, BorderLayout.CENTER); 
		//Video encoding and quality
		imgTranslator = new ImageTranslator(0.8f); 
	}
	
	public static void main(String argv[]) throws Exception{
		Server server = new Server(); 
		
		//Show GUI 
		server.pack();
		server.setVisible(true);
		server.setSize(new Dimension(400, 200));
		
		//get RTSP socket port from command line 
		int RTSPport = Integer.parseInt(argv[0]); 
		server.RTSP_dest_port = RTSPport; 
		
		//Initiate TCP connection with the client for RTSP session
		ServerSocket listenSocket = new ServerSocket(RTSPport); 
		server.RTSPsocket = listenSocket.accept(); 
		listenSocket.close();
		
		//Get client IP address 
		server.clientIPAddr = server.RTSPsocket.getInetAddress(); 
		
		//Initiate RTSPstate 
		state = INIT;
		
		//Set input and output stream for RTSP session: 
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(server.RTSPsocket.getInputStream())); 
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(server.RTSPsocket.getOutputStream())); 
		
		//Wait for the SETUP message from the client
		
		
		int request_type; 
		boolean done = false; 
		while(!done) {
			request_type = server.parseRequest(); 
			
			if (request_type == SETUP) {
				done = true; 
				
				//update RTSP state 
				state = READY; 
				System.out.println("New RTSP state: READY"); 
				
				//send response 
				server.sendResponse(); 
				
				//init the VideoStream object 
				server.video = new VideoStream(videoFileName); 
				
				//init RTP sockets 
				server.RTPsocket = new DatagramSocket();
				server.RTCPsocket = new DatagramSocket(RTCP_RCV_PORT); 
			}
		}
		while (true) {
			request_type = server.parseRequest(); 
			
			if ((request_type == PLAY) && (state == READY)) {
				//send response 
				server.sendResponse();
				
				//start timer 
				server.timer.start();
				server.rtcpReceiver.startRcv();
				
				//update state 
				state = PLAYING; 
				System.out.println("New RTSP state: PLAYING"); 
			}
			else if ((request_type == PAUSE) && (state == PLAYING)) {
				//send response
				server.sendResponse();
				//stop timer
				server.timer.stop();
				server.rtcpReceiver.stopRcv();
				//update state 
				state = READY; 
				System.out.println("New RTSP state: READY"); 
			}
			else if (request_type == TEARDOWN) {
				//send response 
				server.sendResponse();
				
				//stop timer 
				server.timer.stop();
				server.rtcpReceiver.stopRcv();
				//close socket 
				server.RTPsocket.close();
				server.RTSPsocket.close();
				server.RTCPsocket.close();
				System.exit(0);
			}
		}
		
		
	}
	
	
	// request message syntax from client
	/*
	 * request_type + " " + videoFileName + " RTSP/1.0" + CRLF //RequestLine
	 * "CSeq: " + RTSPSeqNb                                    //SeqNumLine
	 * + CRLF SETUP: "Transport: RTP/UDP, client_port: " + RTP_RCV_PORT + CRLF
	 * otherwise: "Session: " + RTSPid + CRLF
	 */
	private int parseRequest() {
		// TODO Auto-generated method stub
		int request_type = -1; 
		try {
			
			//parse the request line and extract the request_type
			String RequestLine = RTSPBufferedReader.readLine(); 
			System.out.println("RTSP Server - Received from Client: "); 
			System.out.println(RequestLine); 
			StringTokenizer tokens = new StringTokenizer(RequestLine); 
			String request_type_string = tokens.nextToken(); 
			
			//convert to request_type structure 
			if ((new String(request_type_string)).compareTo("SETUP") == 0) 
				request_type = SETUP; 
			else if ((new String(request_type_string)).compareTo("PLAY") == 0) 
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0) 
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0) 
				request_type = TEARDOWN;
			
			if (request_type == SETUP) {
				videoFileName = tokens.nextToken(); 
			}
			
			//parse the SeqNumLine and extract the CSeq
			String SeqNumLine = RTSPBufferedReader.readLine(); 
			System.out.println(SeqNumLine); 
			tokens = new StringTokenizer(SeqNumLine); 
			tokens.nextToken(); //Skip CSeq 
			RTSPSeqNb = Integer.parseInt(tokens.nextToken()); 
			
			//get LastLine 
			String LastLine = RTSPBufferedReader.readLine(); 
			System.out.println(LastLine);
			
			tokens = new StringTokenizer(LastLine);
			if (request_type == SETUP) {
				//extract RTP_dest_port from LastLine 
				for (int i = 0; i < 3; i++) {
					tokens.nextToken(); 
				}
				RTP_dest_port = Integer.parseInt(tokens.nextToken()); 
			}
			else {
				//extract RTSPid 
				tokens.nextToken(); 
				RTSPid = tokens.nextToken(); 
			}
		}
		catch(Exception e) {
			System.out.println("Exception caught: " + e);
			System.exit(0);
		}
		return request_type;
	}

	private void sendResponse() {
		// TODO Auto-generated method stub
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
			RTSPBufferedWriter.flush();
			System.out.println("RTSP Server - Sent reponse to Client"); 
		}
		catch(Exception ex) {
			System.out.println("Exception caught: " + ex); 
			System.exit(0);
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		
		//if the current image nb is less than the length of the video 
		if (imagenb < VIDEO_LENGTH) {
			imagenb++;
			
			try {
				int image_length = video.getNextFrame(buf); 
				
				//Builds an RTP packet object containing the frame 
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, buf, image_length); 
				int packet_length = rtp_packet.get_length(); 
				
				//retrieve the packet bitstream and store it in an array of bytes 
				byte[] packet_bits = new byte[packet_length]; 
				rtp_packet.getPacket(packet_bits); 
				
				//send the packet as a DatagramPacket over the UDP socket 
				senddp = new DatagramPacket(packet_bits, packet_length, clientIPAddr, RTP_dest_port); 
				RTPsocket.send(senddp);
				System.out.println("Send frame #" + imagenb + ", Frame size: " + image_length + " (" + buf.length + ") ");
				
				rtp_packet.printHeader();
				
				//updateGUI
				label.setText("Send frame #" + imagenb);
			}
			catch(Exception ex) {
	            System.out.println("Exception caught: "+ex);
                System.exit(0);
			}

		}
		else {
			timer.stop();
		}
	}
	class RtcpReceiver implements ActionListener {
		private Timer rtcpTimer; 
		private byte[] rtcpBuf;
		int interval; 
		
		public RtcpReceiver(int interval) {
			//setup timer 
			this.interval = interval; 
			rtcpTimer = new Timer(interval, this); 
			rtcpTimer.setInitialDelay(0);
			rtcpTimer.setCoalesce(true);
			
			//allocate buffer to receivering RTCP packet
			rtcpBuf = new byte[512]; 
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			//UDP packet to receive RTCP messages 
			DatagramPacket dp = new DatagramPacket(rtcpBuf, rtcpBuf.length); 
			float fractionLost; 
			try {
				RTCPsocket.receive(dp);
				RTCPpacket rtcpPkt = new RTCPpacket(dp.getData(), dp.getLength()); 
				System.out.println("[RTCP] " + rtcpPkt); 
				
				//set congestion level between 0 and 4
				fractionLost = rtcpPkt.fractionLost; 
				if (fractionLost >= 0 && fractionLost <= 0.01) {
					congestionLevel = 0; 
				}
				else if (fractionLost > 0.01 && fractionLost <= 0.25) {
					congestionLevel = 1; 
				}
				else if (fractionLost > 0.25 && fractionLost <= 0.5) {
					congestionLevel = 2; 
				}
				else if (fractionLost > 0.5 && fractionLost <= 0.75) {
					congestionLevel = 3; 
				}
				else {
					congestionLevel = 4; 
				}
			} 
			catch(InterruptedIOException iioe) {
				System.out.println("Nothing to read"); 
			}
			catch(IOException ioe) {
				System.out.println("Exception caught: " + ioe); 
			}
		}
		
		public void startRcv() {
			rtcpTimer.start();
		}
		
		public void stopRcv() {
			rtcpTimer.stop();
		}
	}
	
	private class CongestionController implements ActionListener {
		private Timer ccTimer; 
		int interval; 
		int prevLevel;
		public CongestionController(int interval) {
			// TODO Auto-generated constructor stub
			this.interval = interval; 
			ccTimer = new Timer(interval, this); 
			ccTimer.start();
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			if (prevLevel != congestionLevel) {
				sendDelay = FRAME_PERIOD + congestionLevel + (int)(FRAME_PERIOD * 0.1); 
				timer.setDelay(sendDelay);
				prevLevel = congestionLevel; 
				System.out.println("Send delay change to: " + sendDelay);
			}
		}
	}
	
	class ImageTranslator {
		private float compressionQuality; 
		private ByteArrayOutputStream baos; 
		private BufferedImage image; 
		private Iterator<ImageWriter> writers; 
		private ImageWriter writer; 
		private ImageWriteParam param; 
		private ImageOutputStream ios; 
		
		public ImageTranslator(float cq) {
			compressionQuality = cq; 
			
			try {
				baos = new ByteArrayOutputStream(); 
				ios = ImageIO.createImageOutputStream(baos); 
				writers = ImageIO.getImageWritersByFormatName("jpeg"); 
				writer = (ImageWriter)writers.next(); 
				writer.setOutput(ios);
				param = writer.getDefaultWriteParam(); 
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(compressionQuality);
			}
			catch(Exception ex) {
				System.out.println("Exception caught: " + ex); 
				System.exit(0);
			}
		}
		
		public byte []compress (byte[] imageBytes) {
			try {
				baos.reset();
				image = ImageIO.read(new ByteArrayInputStream(imageBytes)); 
				writer.write(null, new IIOImage(image, null, null), param);
			}
			catch(Exception ex) {
				System.out.println("Exception caught: " + ex); 
				System.exit(0);
			}
			return baos.toByteArray(); 
		}
		
		public void setCompressionQuality(float cq) {
			compressionQuality = cq; 
			param.setCompressionQuality(compressionQuality);
		}
	}
}

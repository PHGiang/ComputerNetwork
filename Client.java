 
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.StringTokenizer;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;


public class Client {
	JFrame f = new JFrame("Client"); 
	JButton setupButton = new JButton("Set up");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton tearButton = new JButton("Close");
	
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;
	
	DatagramPacket rcvdp; 
	DatagramSocket RTPsocket; 
	static int RTP_RCV_PORT = 25000; 
	
	Timer timer; 
	byte []buf; 
	/*
	 * RTSP variables - rtsp states 
	 */
	final static int INIT = 0; 
	final static int READY = 1; 
	final static int PLAYING = 2; 
	static int state; 
	Socket RTSPsocket; 
	InetAddress ServerIPAddress; 
	
	//input and output stream for RTSP messages 
	static BufferedReader RTSPBufferedReader; 
	static BufferedWriter RTSPBufferedWriter; 
	static String videoFileName; 
	int RTSPSeqNb = 0;
	String RTSPid; 
	
	FrameSynchronizer fsynch;
	
	final static String CRLF = "\r\n"; 
	
	//Video constants
	static int MJPEG_TYPE = 26; 
	
	public Client() {
		// TODO Auto-generated constructor stub
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		buttonPanel.setLayout(new GridLayout(1, 0));
		buttonPanel.add(setupButton); 
		buttonPanel.add(playButton); 
		buttonPanel.add(pauseButton); 
		buttonPanel.add(tearButton); 
		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		tearButton.addActionListener(new tearButtonListener());
		
		iconLabel.setIcon(null);
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel, BorderLayout.CENTER); 
		mainPanel.add(buttonPanel, BorderLayout.SOUTH); 
		
		iconLabel.setBounds(0, 0, 380, 300);
		buttonPanel.setBounds(0, 300, 380, 50);
		f.getContentPane().add(mainPanel, BorderLayout.CENTER); 
		f.setSize(new Dimension(430, 370));
		f.setLocation(0, 300);
		f.setVisible(true);
		
		//init timer 
		timer = new Timer(20, new timerListener()); 
		timer.setInitialDelay(0);
		timer.setCoalesce(true);
		
		//allocate enough memory for the buffered used to received data from server 
		buf = new byte[15000]; 
		//create the frame synchronizer 
		fsynch = new FrameSynchronizer(100); 
	}
	
	public static void main(String [] argv) throws Exception{
		Client theClient = new Client(); 
		
		int RTSP_server_port = Integer.parseInt(argv[1]); 
		System.out.println(RTSP_server_port); 
		String server_host = argv[0]; 
		theClient.ServerIPAddress = InetAddress.getByName(server_host); 
		System.out.println("serverhost"+ server_host); 
		
		//get videofilename request 
		videoFileName = argv[2]; 
		System.out.println("Video file name = " + videoFileName); 
		theClient.RTSPsocket = new Socket(theClient.ServerIPAddress, RTSP_server_port);
		System.out.println(theClient.RTSPsocket); 
		
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream())); 
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream())); 
		
		state = INIT; 
	}
	
	class setupButtonListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			System.out.println("Setup Button Pressed!");
			if (state == INIT) {
				System.out.println("state = " + state); 
				try {
					RTPsocket = new DatagramSocket(RTP_RCV_PORT); 
					RTPsocket.setSoTimeout(5);
				}
				catch(SocketException se) {
					System.out.println("Socket exception: " + se);
					System.exit(0);
				}
				
				//Init RTSP sequence number 
				RTSPSeqNb = 1; 
				//send SETUP message to the server 
				sendRequest("SETUP"); 
				// wait for response 
				if (parseServerResponse() != 200) {
					System.out.println("Invalid server response"); 
				} else {
					state = READY; 
					System.out.println("New RTSP state: READY");
				}
			}
		}
	}
	
	class playButtonListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			System.out.println("Play button pressed!"); 
			if (state == READY) {
				RTSPSeqNb++; 
				sendRequest("PLAY");
				if (parseServerResponse() != 200) {
					System.out.println("Invalid server response"); 
				} else {
					state = PLAYING; 
					System.out.println("New RTSP state: PLAYING");
					
					timer.start();
					
				}
			} //state != READY do nothing 
		}
	}
	
	class pauseButtonListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			System.out.println("Pause button pressed!"); 
			
			if (state == PLAYING) {
				RTSPSeqNb++; 
				sendRequest("PAUSE");
				
				if (parseServerResponse() != 200) {
					System.out.println("Invalid server response"); 
				} else {
					state = READY; 
					System.out.println("New RTSP state: READY"); 
					timer.stop();
				}
			}
		}
	}
	
	class tearButtonListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			System.out.println("Teardown button pressed!"); 
			RTSPSeqNb++; 
			sendRequest("TEARDOWN");
			if (state == INIT) {
				System.exit(0);
			} else {
				if (parseServerResponse() != 200) {
					System.out.println("Invalid Server Response");
				} else {
					state = INIT;
					System.out.println("New RTSP state: INIT");
					timer.stop();
					System.exit(0);
				}
			}
		}
	}
	private void sendRequest(String request_type) {
		// TODO Auto-generated method stub
		try {
			//write the requestLine 
			RTSPBufferedWriter.write(request_type + " " + videoFileName + " RTSP/1.0" + CRLF);
			//write the CSeqLine 
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF); 
			//check if request_type is equal to "SET UP" 
			// if "SETUP" send transport type and port PTP_RCV_PORT used to receive RTP packet 
			if (request_type == "SETUP") {
				RTSPBufferedWriter.write("Transport: RTP/UDP, client_port: " + RTP_RCV_PORT + CRLF); 
			}
			else {
				//otherwise write sessionLine from the RTSPid
				RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
			}
			
			RTSPBufferedWriter.flush();
		} catch(Exception ex) {
			System.out.println("Exception caught: " + ex); 
			System.exit(0);
		}
	}
	
	private int parseServerResponse() {
	/*	"RTSP/1.0 200 OK" + CRLF //statusLine 
	 * 	"CSeq: " + RTSPSeqNb + CRLF 
	 * 	"Session: " + RTSPid + CRLF
	 */
		int reply_code = 0; 
		try {
			//parse status line and extract reply_code
			String StatusLine = RTSPBufferedReader.readLine(); 
			System.out.println("RTSP Client - Received from Server: "); 
			System.out.println(StatusLine); 
			
			StringTokenizer tokens = new StringTokenizer(StatusLine); 
			tokens.nextToken(); 
			reply_code = Integer.parseInt(tokens.nextToken()); 
			if (reply_code == 200) {
				String seqNumLine = RTSPBufferedReader.readLine(); 
				System.out.println(seqNumLine); 
				
				String sessionLine = RTSPBufferedReader.readLine(); 
				System.out.println(sessionLine); 
				
				tokens = new StringTokenizer(sessionLine); 
				String tmp = tokens.nextToken(); 
				//if state == INIT get SessionId from the Session Line 
				
				if (state == INIT && tmp.compareTo("Session:") == 0) {
					String newLine;
					RTSPid = tokens.nextToken(); 
				}
				
			}
			
		} catch(Exception e) {
			System.out.println("Exception caught: " + e); 
			System.exit(0);
		}
		return reply_code; 
	}
	
	class timerListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			rcvdp = new DatagramPacket(buf, buf.length); 
			
			try {
				RTPsocket.receive(rcvdp);
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength()); 
				int seqNb = rtp_packet.getSequenceNumber(); 
				// print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum #" + seqNb + " Timestamp " + rtp_packet.getTimestamp()
						+ " ms, of type: " + rtp_packet.getPayloadType());
				rtp_packet.printHeader();
				
				//get payload bitstream from the rtp_packet 
				int payload_length = rtp_packet.getPayload_length(); 
				byte[]payload = new byte[payload_length]; 
				rtp_packet.getPayload(payload); 

				//get image from payload stream 
				Toolkit toolkit = Toolkit.getDefaultToolkit(); 
				fsynch.addFrame(toolkit.createImage(payload, 0, payload_length), seqNb);
				
				//display the image as and ImageIcon object 
				icon = new ImageIcon(fsynch.nextFrame()); 
				iconLabel.setIcon(icon);
			} catch(InterruptedIOException iioe) {
				System.out.println("Nothing to read"); 
			} catch(IOException ioe) {
				System.out.println("Exception caught: " + ioe); 
			}
		}
		
	}
	
	class FrameSynchronizer {
		private ArrayDeque<Image> queue; 
		private int bufSize; 
		private int curSeqNb;
		private Image lastImage;

		public FrameSynchronizer(int bsize) {
			curSeqNb = 1;
			bufSize = bsize;
			queue = new ArrayDeque<Image>(bufSize);
		}
		
		public void addFrame(Image image, int seqNum) {
			if (seqNum < curSeqNb) {
				queue.add(lastImage); 
			} else if (seqNum > curSeqNb) {
				for (int i = curSeqNb; i < seqNum; i++) {
					queue.add(lastImage); 
				}
				queue.add(image); 
			} else {
				queue.add(image); 
			}
		}
		
		public Image nextFrame() {
			curSeqNb++; 
			lastImage = queue.peekLast(); 
			return (queue.remove()); 
		}
	}
}

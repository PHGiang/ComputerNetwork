

import java.io.FileInputStream;

public class VideoStream {

	FileInputStream fis; 
	int frame_nb; 
	
	public VideoStream(String filename) throws Exception  {
		// TODO Auto-generated constructor stub
		fis = new FileInputStream(filename); 
		frame_nb = 0; 
	}
	/*
	 * return next frame as an array of byte: frame 
	 * return length of frame
	 * */
	public int getNextFrame(byte[] frame) throws Exception {
		System.out.println("Welcome to get next frame"); 
		int length = 0;
		String length_string; 
		byte[] frame_length = new byte[5]; 
		
		//read current frame length 
		int m = fis.read(frame_length, 0, 5);
		System.out.println("m = " + m); 
		System.out.println("frame_length " + frame_length);
		
		
		//transform frame_length to integer 
		length_string = new String(frame_length);
		System.out.println("Length in string of frame: " + length_string); 
		length = Integer.parseInt(length_string); 
		int n = fis.read(frame, 0, length);
		System.out.println("frame = " + frame);
		System.out.println("n = " + n); 
		return n; 
	}
}

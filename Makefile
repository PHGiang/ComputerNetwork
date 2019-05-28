JFLAGS = -g
JC = javac

.SUFFIXES: .java .class
	
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
		VideoStream.java \
		RTPpacket.java \
		RTCPpacket.java \
		Client.java \
		Server.java

default: classes 

classes: $(CLASSES:.java=.class)

clean: 
	$(RM) *.class 
# define a makefile variable for the java compiler
#
all: UDPClient

UDPClient: UDPClient.java
	javac -d . UDPClient.java
	
run: 
	java UDPClient -s $(s) -p $(p)

clean:
	rm -f *.class 
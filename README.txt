Read Me

This program can be executed in any environment that has Java installed. 

Please follow the steps:

1. cd to makefile directory
2. Compile the program, using: make UDPClient
3. Run the program, passing correct arguments
	make run s=DNS.POSTEL.ORG p=60450
	or
	java UDPClient -s DNS.POSTEL.ORG -p 60450
	or 
	execute without param(using default name server and port)

4. After run the program, do "make clean"

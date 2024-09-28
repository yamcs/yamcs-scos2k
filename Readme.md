This project implements a SCOS-2000 MIB loader inside Yamcs.

To use it, please add yamcs-scos2k as a dependency in pom.xml.

Then you can add the following in the mdb configuration:

<pre>
mdb:
  - type: "org.yamcs.scos2k.MibLoader"
    args: 
        path: "/path/to/ASCII/"        
        epoch: "1970-01-01T00:00:00"
        #TC specific settings
        TC:   
           # default size in bytes of the size tag for variable length strings and bytestrings
           vblParamLengthBytes: 0
        #TM specific settings
        TM:  
            vblParamLengthBytes: 1
            # byte offset from beginning of the packet where the type and subType are read from     
            typeOffset: 7
            subTypeOffset: 8
</pre>

Note: currently all the test files are not stored as part of this project (they are stored in a private repository) because the MIB used for test is taken from another project and cannot be made public. If anyone is willing to contribute a MIB that can be made public, we would gladly change the tests to use that MIB instead.

The project implements a service that loads the MIB alphanumeric displays into Yamcs parameter lists. To use it, please add the MibDisplayLoader to your service list:

<pre>
services:
  ....
  #this is required to have the parameter lists functionality
  - class: org.yamcs.plists.ParameterListService
  # this is loading the MIB 
  - class: org.yamcs.scos2k.MibDisplayLoader
    args:
      mibPath: "path/to/ASCII"

</pre>
Node that the parameter lists created by the service can be modified but will not be persisted (the MIB files are not changed). The modifications will be overwritten upon restarting Yamcs.

# Known problems and limitations
* delta monitoring checks (OCP_TYPE=D) not supported
* event generation (OCP_TYPE=E) not supported
* status consistency checks (OCP_TYPE=C, PCF_USCON=Y) not supported
* arguments of type "Command Id" (CPC_CATEG=A) are not supported. These can be used to insert one command inside another one. Instead a binary command argument is being used.
* SCOS 2K allows multiple command arguments with the same name. This is not supported in Yamcs (and in XTCE) so duplicate arguments are renamed arg_&lt;n&gt;  with n increasing for each argument. 
* no command stack support.
 

This project implements a SCOS-2000 MIB loader inside Yamcs.

To use it, first clone the repo and compile it using:
mvn install

Then you can add the following in the mdb.yaml:
<pre>
scos-mib:
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


# Known problems and limitations

To be fixed soon:

* the loader does not detect when the files have changed and does not reload the database. This is because it looks at the date  of the ASCII directory. As a workaround you can either remove the serialized MDB from ~/.yamcs or /opt/yamcs/cache or run "touch ASCII" to change the date of the directory.



Not immediate priority:

* delta monitoring checks (OCP_TYPE=D) not supported
* event generation (OCP_TYPE=E) not supported
* status consistency checks (OCP_TYPE=C, PCF_USCON=Y) not supported
* arguments of type "Command Id" (CPC_CATEG=A) are not supported. These can be used to insert one command inside another one. Instead a binary command argument is being used.
* SCOS 2K allows multiple command arguments with the same name. This is not supported in Yamcs (and in XTCE) so duplicate arguments are renamed arg_&lt;n&gt;  with n increasing for each argument. 
* no command stack support.
 

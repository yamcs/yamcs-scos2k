SCOS-2000 MIB Loader
====================


Usage
-----

The following can be added to the mdb section of the instance configuration:

.. code-block:: yaml
  mdb:
  - type: "org.yamcs.scos2k.MibLoader"
    args: 
        path: "/path/to/ASCII/"        
        epoch: "1970-01-01T00:00:00"
        TC:   
           # default size in bytes of the size tag for variable length strings and bytestrings
           vblParamLengthBytes: 0
        TM:  
            vblParamLengthBytes: 1
            # byte offset from beginning of the packet where the type and subType are read from     
            typeOffset: 7
            subTypeOffset: 8
            
Options
-------

path (string)
    **Required** The path of the directory containing the MIB ASCII files.

epoch (string)
    One of TAI, J2000, UNIX, GPS or a UTC datestring. The epoch is used as epoch for all time parameters (PTC 9). 
    If TAI, J2000 or GPS are used, the time is assumed to include leap seconds (which are then removed when coverting to UTC) whereas if the epoch is specified as UNIX or using a UTC datestring, the time is assumed to not include leap seconds.
    Note that internally Yamcs stores the time with leap seconds referenced to 1970-01-01 TAI epoch so when decoding the parameters it will covert the values to this internal epoch (adding the necessary leap seconds if they are not already there).
    

TC Options
----------
These options are specified under the TC keyword

vblParamLengthBytes(interger)
     default size in bytes of the size tag for variable length strings and bytestrings

TM Options
----------
These options are specified under the TM keyword

vblParamLengthBytes (integer)
typeOffset (integer)
subTypeOffset (integer)


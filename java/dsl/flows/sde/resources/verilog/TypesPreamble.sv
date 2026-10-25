package Types;

function automatic logic[63:0] combineInternetChecksum(logic[63:0] a, logic[63:0] b);
    return a[0+:16]+a[16+:16]+a[32+:16]+a[48+:16]+b[0+:16]+b[16+:16]+b[32+:16]+b[48+:16];
endfunction

typedef struct packed {
    bit[511:0] Data;
    bit[182:0] User;
    bit[15:0] Keep;
    bit Last;
    bit Valid;
} PCIEProducer;

typedef struct packed {
    bit [2:0] Attributes;
    bit [2:0] TransactionClass;
    bit [15:0] RequesterID;
    bit [7:0] Tag;
    bit [1:0] AddressType;
    bit [6:0] AddressLow7;
} ReadCompletionStruct;

typedef struct packed {
    ReadCompletionStruct ReadCompletionField;
    bit [64:0] Padding;
} PaddedReadCompletionStruct;

typedef struct packed {
    bit [95:0] WriteData;
    bit [3:0] FirstByteEnable;
    bit [3:0] LastByteEnable;
} WriteDataStruct;

typedef struct packed {
    bit [17:2] Address;
    bit [1:0] Length;
    bit OperationIsWrite;
    union packed {
        PaddedReadCompletionStruct PaddedReadCompletionField;
        WriteDataStruct WriteDataField;
    } OperationDetails;
} AXIRequest;

typedef struct packed {
    ReadCompletionStruct ReadCompletionField;
    bit [1:0] Length;
    bit [95:0] ReadData;
    bit [3:0] TotalBytes;
} AXIResponse;

typedef struct packed {
    bit Stop;
    bit[31:0] Data;
} ToCoreWord;

typedef struct packed {
    bit Stop;
    bit[30:0] WordA;
    bit ValidB;
    bit[30:0] WordB;
} FromCoreWord;

typedef struct packed {
    bit[6:0] Reader;
    bit[6:0] Writer;
} QueuePointers7;

// the rest, including endpackage, appended by generator

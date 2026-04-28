// [AG-FIX 2.5] Intel HEX format writer (IHEX8M)
public final class IntelHexWriter {
    private IntelHexWriter(){}
    // Convert raw bytes + start address to Intel HEX string
    public static String convert(int[] data, int startAddr) {
        StringBuilder sb=new StringBuilder();
        int offset=0;
        while(offset<data.length){
            int len=Math.min(16, data.length-offset);
            int addr=(startAddr+offset)&0xFFFF;
            int sum=len+((addr>>8)&0xFF)+(addr&0xFF); // type=00
            sb.append(String.format(":%02X%04X00",len,addr));
            for(int i=0;i<len;i++){
                int b=data[offset+i]&0xFF;
                sb.append(String.format("%02X",b));
                sum+=b;
            }
            sb.append(String.format("%02X\n",(-sum)&0xFF));
            offset+=len;
        }
        sb.append(":00000001FF\n"); // EOF record
        return sb.toString();
    }
}

package org.monacoin.crypto;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.math.BigInteger;

public class Lyra2v2 {

    public static final int BLOCK_LEN_BITS = 768; /* default */
    public static final int BLOCK_LEN_BYTES = BLOCK_LEN_BITS / 8;
    public static final int BLOCK_LEN_INT64 = BLOCK_LEN_BITS / 64;
    public static final int BLOCK_LEN_BLAKE2_SAFE_INT64 = 8;
    public static final int BLOCK_LEN_BLAKE2_SAFE_BYTES = BLOCK_LEN_BLAKE2_SAFE_INT64 * 8;
    
    /* sponge.h */

    protected static final long[] blake2b_IV = {
        0x6A09E667F3BCC908L, 0xBB67AE8584CAA73BL,
        0x3C6EF372FE94F82BL, 0xA54FF53A5F1D36F1L,
        0x510E527FADE682D1L, 0x9B05688C2B3E6C1FL,
        0x1F83D9ABFB41BD6BL, 0x5BE0CD19137E2179L
    };

    protected static long rotr64( long w , int c ){
        return (w >>> c) | (w << (64 - c));
    }

    /* long: +,-,* の結果のバイナリ表現上は、signed/unsignedで同じ */
    
    protected static void g( long[] v , int a , int b , int c , int d ){

        v[a] = v[a] + v[b];
        v[d] = rotr64(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d];
        v[b] = rotr64(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b];
        v[d] = rotr64(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = rotr64(v[b] ^ v[c], 63);

    }

    // ここにもんだいあり
    protected static void roundLyra( long[] v ){
        g(v , 0, 4, 8,12);
        g(v , 1, 5, 9,13);
        g(v , 2, 6,10,14);
        g(v , 3, 7,11,15);
        g(v , 0, 5,10,15);
        g(v , 1, 6,11,12);
        g(v , 2, 7, 8,13);
        g(v , 3, 4, 9,14);
    }

    /* sponge.c */

    protected static long[] initState(){
        long[] state = new long[16];
        java.util.Arrays.fill( state , 0L);
        state[8] = blake2b_IV[0];
        state[9] = blake2b_IV[1];
        state[10] = blake2b_IV[2];
        state[11] = blake2b_IV[3];
        state[12] = blake2b_IV[4];
        state[13] = blake2b_IV[5];
        state[14] = blake2b_IV[6];
        state[15] = blake2b_IV[7];

        return state;
    }

    protected static void blake2bLyra(long[] v){
        for(int i = 0;i < 12;i++){
            roundLyra( v );
        }
    }

    protected static void reducedBlake2bLyra( long[] v){
        roundLyra(v);
    }

    protected static void squeeze( long[] state , byte[] outbuf , int len){
        int fullBlocks = len / BLOCK_LEN_BYTES;
        ByteBuffer bb = ByteBuffer.wrap( outbuf );
        bb.order( java.nio.ByteOrder.LITTLE_ENDIAN);
        //Squeezes full blocks
        for(int i = 0; i < fullBlocks; i++){
            for(int j = 0; j < BLOCK_LEN_INT64;j++){
                bb.putLong( state[j] );
            }
            blake2bLyra(state);
        }
        //Squeezes remaining bytes
        byte[] remain = new byte[ state.length * 8 ];
        ByteBuffer remainBB = ByteBuffer.wrap( remain );
        remainBB.order( java.nio.ByteOrder.LITTLE_ENDIAN);
        for(int j=0;j< state.length ;j++){
            remainBB.putLong( state[j] );
        }
        bb.put( remain , 0 , (len % BLOCK_LEN_BYTES) );
    }
    
    protected static void absorbBlock( long[] state , LongBuffer in){
        //XORs the first BLOCK_LEN_INT64 words of "in" with the current state
        state[0] ^= in.get(0);
        state[1] ^= in.get(1);
        state[2] ^= in.get(2);
        state[3] ^= in.get(3);
        state[4] ^= in.get(4);
        state[5] ^= in.get(5);
        state[6] ^= in.get(6);
        state[7] ^= in.get(7);
        state[8] ^= in.get(8);
        state[9] ^= in.get(9);
        state[10] ^= in.get(10);
        state[11] ^= in.get(11);
    
        //Applies the transformation f to the sponge's state
        blake2bLyra(state);
    }

    protected static void  absorbBlockBlake2Safe(long[] state , LongBuffer in){
        //XORs the first BLOCK_LEN_BLAKE2_SAFE_INT64 words of "in" with the current state
        
        state[0] ^= in.get(0);
        state[1] ^= in.get(1);
        state[2] ^= in.get(2);
        state[3] ^= in.get(3);
        state[4] ^= in.get(4);
        state[5] ^= in.get(5);
        state[6] ^= in.get(6);
        state[7] ^= in.get(7);

        //Applies the transformation f to the sponge's state
        blake2bLyra(state);
        
    }

    public static void reducedSqueezeRow0( long[] state , LongBuffer rowOut , long nCols ){
        int offset = (int)((nCols-1)*BLOCK_LEN_INT64);
        for(int i=0;i<nCols;i++){
            
            rowOut.put(offset+0,  state[0]) ;
            rowOut.put(offset+1,  state[1]) ;
            rowOut.put(offset+2,  state[2]) ;
            rowOut.put(offset+3,  state[3]) ;
            rowOut.put(offset+4,  state[4]) ;
            rowOut.put(offset+5,  state[5]) ;
            rowOut.put(offset+6,  state[6]) ;
            rowOut.put(offset+7,  state[7]) ;
            rowOut.put(offset+8,  state[8]) ;
            rowOut.put(offset+9,  state[9]) ;
            rowOut.put(offset+10,  state[10]) ;
            rowOut.put(offset+11,  state[11]) ;

            //Goes to next block (column) that will receive the squeezed data
            offset -= BLOCK_LEN_INT64;
            
            //Applies the reduced-round transformation f to the sponge's state
            reducedBlake2bLyra(state);
        }
    }

    protected static void reducedDuplexRow1( long[] state , LongBuffer rowIn , 
                                            LongBuffer rowOut , long nCols){
        int inOffset = 0;
        int outOffset = (int)((nCols-1)*BLOCK_LEN_INT64);

        for(int i = 0;i<nCols;i++){
            //Absorbing "M[prev][col]"
            for(int j = 0; j <= 11;j++)
                state[j] ^= rowIn.get( inOffset + j );

            //Applies the reduced-round transformation f to the sponge's state
            reducedBlake2bLyra(state);

            for(int j = 0; j<= 11;j++)
                rowOut.put( outOffset + j , ( rowIn.get(inOffset + j) ^ state[j]));

            //Input: next column (i.e., next block in sequence)
            inOffset += BLOCK_LEN_INT64;
            //Output: goes to previous column
            outOffset -= BLOCK_LEN_INT64;
        }
    }

    protected static void reducedDuplexRowSetup( long[] state , LongBuffer rowIn , 
                                                 LongBuffer rowInOut,
                                                 LongBuffer rowOut , long nCols){
        int inOffset = 0;
        int inOutOffset = 0;
        int outOffset = (int)((nCols-1)*BLOCK_LEN_INT64);

        for(int i=0;i<nCols;i++){
            //Absorbing "M[prev] [+] M[row*]"
           for(int j=0;j<=11;j++)
                state[j] ^= ( rowIn.get(inOffset+j) + rowInOut.get(inOutOffset+j) );

            //Applies the reduced-round transformation f to the sponge's state
            reducedBlake2bLyra(state);

            //M[row][col] = M[prev][col] XOR rand
            for(int j=0;j<=11;j++)
                rowOut.put( outOffset+j , (rowIn.get(inOffset+j) ^ state[j]));

            //M[row*][col] = M[row*][col] XOR rotW(rand)
            rowInOut.put( inOutOffset+0 , (rowInOut.get(inOutOffset+0) ^ state[11]));
            rowInOut.put( inOutOffset+1 , (rowInOut.get(inOutOffset+1) ^ state[0]));
            rowInOut.put( inOutOffset+2 , (rowInOut.get(inOutOffset+2) ^ state[1]));
            rowInOut.put( inOutOffset+3 , (rowInOut.get(inOutOffset+3) ^ state[2]));
            rowInOut.put( inOutOffset+4 , (rowInOut.get(inOutOffset+4) ^ state[3]));
            rowInOut.put( inOutOffset+5 , (rowInOut.get(inOutOffset+5) ^ state[4]));
            rowInOut.put( inOutOffset+6 , (rowInOut.get(inOutOffset+6) ^ state[5]));
            rowInOut.put( inOutOffset+7 , (rowInOut.get(inOutOffset+7) ^ state[6]));
            rowInOut.put( inOutOffset+8 , (rowInOut.get(inOutOffset+8) ^ state[7]));
            rowInOut.put( inOutOffset+9 , (rowInOut.get(inOutOffset+9) ^ state[8]));
            rowInOut.put( inOutOffset+10, (rowInOut.get(inOutOffset+10) ^ state[9]));
            rowInOut.put( inOutOffset+11, (rowInOut.get(inOutOffset+11) ^ state[10]));

            inOutOffset += BLOCK_LEN_INT64;
            inOffset += BLOCK_LEN_INT64;
            outOffset -= BLOCK_LEN_INT64;
        }
    }

    protected static void reducedDuplexRow( long[] state, 
                                            LongBuffer rowIn , LongBuffer rowInOut,
                                            LongBuffer rowOut, long nCols){
        int inOutOffset = 0;
        int inOffset = 0;
        int outOffset = 0;
        
        for(int i=0;i<nCols;i++){
            //Absorbing "M[prev] [+] M[row*]"
            for(int j=0;j<=11;j++)
                state[j] ^= rowIn.get(inOffset+j) + rowInOut.get(inOutOffset+j);

            //Applies the reduced-round transformation f to the sponge's state
            reducedBlake2bLyra(state);

            //M[rowOut][col] = M[rowOut][col] XOR rand
            for(int j=0;j<=11;j++)
                rowOut.put( outOffset+j , ( rowOut.get(outOffset+j) ^ state[j]));

            //M[rowInOut][col] = M[rowInOut][col] XOR rotW(rand)
            for(int j=0;j<=11;j++){
                int si = (j == 0) ? 11 : j - 1;
                rowInOut.put( inOutOffset+j , (rowInOut.get(inOutOffset+j) ^ state[si]));
            }

            outOffset += BLOCK_LEN_INT64;
            inOutOffset += BLOCK_LEN_INT64;
            inOffset += BLOCK_LEN_INT64;
        }
    }

    /* Lyra2.c */
    public static void lyra2v2( byte[] k , byte[] pwd , byte[] salt ,
                                long timeCost , 
                                long nRows , 
                                long nCols ){
        
        //============================= Basic variables ============================//
        int row = 2; //index of row to be processed
        int prev = 1; //index of prev (last row ever computed/modified)
        int rowa = 0; //index of row* (a previous row, deterministically picked during Setup and randomly picked while Wandering)
        int tau; //Time Loop iterator
        int step = 1; //Visitation step (used during Setup and Wandering phases)
        int window = 2; //Visitation window (used to define which rows can be revisited during Setup)
        int gap = 1; //Modifier to the step, assuming the values 1 or -1
        int i; //auxiliary iteration counter
        //==========================================================================/
        
        //========== Initializing the Memory Matrix and pointers to it =============//
        //Tries to allocate enough space for the whole memory matrix
        
        final long ROW_LEN_INT64 = BLOCK_LEN_INT64 * nCols;
        final long ROW_LEN_BYTES = ROW_LEN_INT64 * 8;

        i = (int)(nRows * ROW_LEN_BYTES);
        byte[] wholeMatrix = new byte[i];
        java.util.Arrays.fill( wholeMatrix , (byte)0 );
        ByteBuffer wholeMatrixBB = ByteBuffer.wrap( wholeMatrix );
        wholeMatrixBB.order( java.nio.ByteOrder.LITTLE_ENDIAN );
        wholeMatrixBB.position(0);
        LongBuffer wholeMatrixLB = wholeMatrixBB.asLongBuffer();

        int byteOffset;

        //Allocates pointers to each row of the matrix
        LongBuffer[] memMatrix = new LongBuffer[(int)nRows];
  
        //Places the pointers in the correct positions
        for(i=0;i<nRows;i++){
            int pos = (int)(i * ROW_LEN_INT64);
            wholeMatrixLB.position(pos);
            memMatrix[i] = wholeMatrixLB.slice();
        }
        
        //==========================================================================/
        
        //============= Getting the password + salt + basil padded with 10*1 ===============//
        //OBS.:The memory matrix will temporarily hold the password: not for saving memory,
        //but this ensures that the password copied locally will be overwritten as soon as possible
        long nBlocksInput = 
            ((salt.length + pwd.length + 6 * 8 ) / BLOCK_LEN_BLAKE2_SAFE_BYTES)+1;

        wholeMatrixBB.rewind();

        wholeMatrixBB.put( pwd );
        wholeMatrixBB.put( salt );
        wholeMatrixBB.putLong( (long)k.length );
        wholeMatrixBB.putLong( (long)pwd.length );
        wholeMatrixBB.putLong( (long)salt.length );
        wholeMatrixBB.putLong( timeCost );
        wholeMatrixBB.putLong( nRows );
        wholeMatrixBB.putLong( nCols );

        wholeMatrixBB.put( (byte)0x80 );
        
        byteOffset = (int)(nBlocksInput * BLOCK_LEN_BLAKE2_SAFE_BYTES - 1);

        wholeMatrixBB.put( byteOffset , (byte)(wholeMatrixBB.get(byteOffset) ^ (byte)0x01) );

        //==========================================================================/

        //======================= Initializing the Sponge State ====================//
        //Sponge state: 16 uint64_t, BLOCK_LEN_INT64 words of them for the bitrate (b) and the remainder for the capacity (c)
        
        long[] state = initState();
        
        //==========================================================================/

        //================================ Setup Phase =============================//
        //Absorbing salt, password and basil: this is the only place in which the block length is hard-coded to 512 bits
        
        int longOffset = 0;
        for(i=0;i<nBlocksInput;i++){
            wholeMatrixLB.position( longOffset );
            LongBuffer sl = wholeMatrixLB.slice();
            absorbBlockBlake2Safe( state , wholeMatrixLB.slice() );
            longOffset += (int)BLOCK_LEN_BLAKE2_SAFE_INT64;
        }
        
        //Initializes M[0] and M[1]
        reducedSqueezeRow0( state , memMatrix[0] , nCols );

        reducedDuplexRow1( state , memMatrix[0] , memMatrix[1] , nCols);

        do {
            reducedDuplexRowSetup( state , memMatrix[prev] , memMatrix[rowa],
                                   memMatrix[row] , nCols );
            rowa = (rowa + step) & (window - 1);
            prev = row;
            row++;

            if(rowa==0){
                step = window + gap;
                window *= 2;
                gap = -gap;
            }
        } while( row < nRows);
        
        row = 0;
        for(tau = 1;tau <= timeCost;tau++){
            step = (tau % 2 == 0) ? -1 : (int)nRows / 2 - 1;
            do {
                // rowa = (int)(state[0] % nRows);  // ここをunsignedとして
                if(state[0]<0){
                    rowa = (new BigInteger(Long.toHexString(state[0]),16))
                        .mod( new BigInteger(Long.toHexString(nRows),16)).intValue();
                } else
                    rowa = (int)(state[0] % nRows);

                reducedDuplexRow( state , memMatrix[prev] , memMatrix[rowa],
                                  memMatrix[row],nCols);
                prev = row;
                row = (row + step) % (int)nRows;
            } while( row != 0);
        }

        absorbBlock(state , memMatrix[rowa]);
        
        squeeze( state , k , k.length  );
        
    }
}

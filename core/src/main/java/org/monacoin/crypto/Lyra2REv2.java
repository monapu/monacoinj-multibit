package org.monacoin.crypto;

import fr.cryptohash.*;
import org.monacoin.crypto.Lyra2v2;

public class Lyra2REv2 {
    
    protected BLAKE256 blake = new BLAKE256();
    protected Keccak256 keccak = new Keccak256();
    protected CubeHash256 cubeHash = new CubeHash256();
    protected Skein256 skein = new Skein256();
    protected BMW256 bmw = new BMW256();

    public byte[] lyra2REv2( byte[] input ){

        byte[] hashA = new byte[32];
        byte[] hashB = new byte[32];
    
        blake.reset();
        blake.update( input , 0 , 80 );
        blake.digest( hashA , 0 , 32);
        
        keccak.reset();
        keccak.update( hashA , 0 , 32);
        keccak.digest( hashB , 0 , 32);
        
        cubeHash.reset();
        cubeHash.update( hashB , 0 , 32);
        cubeHash.digest( hashA , 0 , 32);

        Lyra2v2.lyra2v2( hashB , hashA , hashA , 1, 4 , 4);

        skein.reset();
        skein.update( hashB , 0 , 32);
        skein.digest( hashA , 0 , 32);

        cubeHash.reset();
        cubeHash.update( hashA , 0 , 32);
        cubeHash.digest( hashB , 0 , 32);

        bmw.reset();
        bmw.update( hashB , 0 , 32);
        bmw.digest( hashA , 0 , 32);

        return hashA;
    }
}

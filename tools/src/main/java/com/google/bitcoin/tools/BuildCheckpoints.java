package com.google.bitcoin.tools;

import com.google.bitcoin.core.*;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.MemoryBlockStore;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.net.discovery.SeedPeers;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Downloads and verifies a full chain from your local peer, emitting checkpoints at each difficulty transition period
 * to a file which is then signed with your key.
 */
public class BuildCheckpoints {
    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();
        final NetworkParameters params = MainNetParams.get();

        // Sorted map of UNIX time of block to StoredBlock object.
        final TreeMap<Integer, StoredBlock> checkpoints = new TreeMap<Integer, StoredBlock>();

        // Configure bitcoinj to fetch only headers, not save them to disk, connect to a local fully synced/validated
        // node and to save block headers that are on interval boundaries, as long as they are <1 month old.
        final BlockStore store = new MemoryBlockStore(params);
        final BlockChain chain = new BlockChain(params, store);
        final PeerGroup peerGroup = new PeerGroup(params, chain);
        if(args.length == 0){
            // peerGroup.addAddress(InetAddress.getLocalHost());
            peerGroup.addPeerDiscovery(new SeedPeers(params));
        } else {
            for(String host:args){
                peerGroup.addAddress( InetAddress.getByName( host ));
            }
        }
        
        long now = new Date().getTime() / 1000;
        peerGroup.setFastCatchupTimeSecs(now);

        final long oneMonthAgo = now - (86400 * 14); //TODO: Make it higher later on

        final byte[] buf = new byte[8];
        final FileOutputStream targetCache = new FileOutputStream("targets", false);
        long startTargetCache = params.getSwitchKGWBlock() - 6720;
        if(startTargetCache < 0 )
            startTargetCache = 0;
        Utils.uint64ToByteArrayLE( startTargetCache , buf , 0);
        final long startTargetCacheIn = startTargetCache;
        targetCache.write( buf , 0 , 8 );

        chain.addListener(new AbstractBlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                int height = block.getHeight();
                if (height % params.getInterval() == 0 && block.getHeader().getTimeSeconds() <= oneMonthAgo) {
                    System.out.println(String.format("Checkpointing block %s at height %d",
                            block.getHeader().getHash(), block.getHeight()));
                    checkpoints.put(height, block);
                }

                if( startTargetCacheIn <= height &&  
                    block.getHeader().getTimeSeconds() <= oneMonthAgo &&
                    height < params.getSwitchDigishieldBlock()){
                    Utils.uint32ToByteArrayLE(block.getHeader().getDifficultyTarget(), buf , 0);
                    Utils.uint32ToByteArrayLE(block.getHeader().getTimeSeconds() , buf , 4);
                    try {
                        targetCache.write( buf );
                    } catch( java.io.IOException e ){

                    }
                }
            }
        }, Threading.SAME_THREAD);

        peerGroup.startAndWait();
        peerGroup.downloadBlockChain();

        targetCache.close();

        checkState(checkpoints.size() > 0);

        // Write checkpoint data out.
        final FileOutputStream fileOutputStream = new FileOutputStream("checkpoints", false);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
        digestOutputStream.on(false);
        final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
        dataOutputStream.writeBytes("CHECKPOINTS 1");
        dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
        digestOutputStream.on(true);
        dataOutputStream.writeInt(checkpoints.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for (StoredBlock block : checkpoints.values()) {
            block.serializeCompact(buffer);
            dataOutputStream.write(buffer.array());
            buffer.position(0);
        }
        dataOutputStream.close();
        Sha256Hash checkpointsHash = new Sha256Hash(digest.digest());
        System.out.println("Hash of checkpoints data is " + checkpointsHash);
        digestOutputStream.close();
        fileOutputStream.close();

        peerGroup.stopAndWait();
        store.close();

        // Sanity check the created file.
        CheckpointManager manager = new CheckpointManager(params, new FileInputStream("checkpoints"));
        checkState(manager.numCheckpoints() == checkpoints.size());
        StoredBlock test = manager.getCheckpointBefore( 1388934000 );
        checkState(test.getHeight() == 10560);
        checkState(test.getHeader().getHashAsString().equals("86153c02cf2c98db355959da59645c36a99499bfa177ebf6f3edb6c9704ccc61"));
    }
}

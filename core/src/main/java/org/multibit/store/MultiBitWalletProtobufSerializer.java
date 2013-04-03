package org.multibit.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Protos.Wallet.EncryptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutPoint;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletExtension;
import com.google.bitcoin.core.WalletTransaction;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.crypto.EncryptedPrivateKey;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;


public class MultiBitWalletProtobufSerializer extends WalletProtobufSerializer {

    private static final Logger log = LoggerFactory.getLogger(MultiBitWalletProtobufSerializer.class);

    // Early version of name-value value for use in protecting encrypted wallets from being loaded
    // into earlier versions of MultiBit. Unfortunately I merged this into the MultiBit v0.4 code by mistake.
    // @deprecated replaced by ORG_MULTIBIT_WALLET_PROTECT_2
    public static final String ORG_MULTIBIT_WALLET_PROTECT = "org.multibit.walletProtect";

    public static final String ORG_MULTIBIT_WALLET_PROTECT_2 = "org.multibit.walletProtect.2";

    public MultiBitWalletProtobufSerializer() {
        super();
    }
    
    /**
     * Converts the given wallet to the object representation of the protocol buffers. This can be modified, or
     * additional data fields set, before serialization takes place.
     */
    public Protos.Wallet walletToProto(Wallet wallet) {
        Protos.Wallet.Builder walletBuilder = Protos.Wallet.newBuilder();
        walletBuilder.setNetworkIdentifier(wallet.getNetworkParameters().getId());
        if (wallet.getDescription() != null) {
            walletBuilder.setDescription(wallet.getDescription());
        }
        
        for (WalletTransaction wtx : wallet.getWalletTransactions()) {
            Protos.Transaction txProto = makeTxProto(wtx);
            walletBuilder.addTransaction(txProto);
        }
        
        for (ECKey key : wallet.getKeys()) {
            Protos.Key.Builder buf = Protos.Key.newBuilder().setCreationTimestamp(key.getCreationTimeSeconds() * 1000)
                                                         // .setLabel() TODO
                                                            .setType(Protos.Key.Type.ORIGINAL);
            if (key.getPrivKeyBytes() != null)
                buf.setPrivateKey(ByteString.copyFrom(key.getPrivKeyBytes()));

            EncryptedPrivateKey encryptedPrivateKey = key.getEncryptedPrivateKey();
            if (encryptedPrivateKey != null) {
                Protos.EncryptedPrivateKey.Builder encryptedKeyBuilder = Protos.EncryptedPrivateKey.newBuilder()
                    .setEncryptedPrivateKey(ByteString.copyFrom(encryptedPrivateKey.getEncryptedBytes()))
                    .setInitialisationVector(ByteString.copyFrom(encryptedPrivateKey.getInitialisationVector()));
                buf.setEncryptedPrivateKey(encryptedKeyBuilder);
            }

            // We serialize the public key even if the private key is present for speed reasons: we don't want to do
            // lots of slow EC math to load the wallet, we prefer to store the redundant data instead. It matters more
            // on mobile platforms.
            buf.setPublicKey(ByteString.copyFrom(key.getPubKey()));
            walletBuilder.addKey(buf);
        }

        // Populate the lastSeenBlockHash field.
        Sha256Hash lastSeenBlockHash = wallet.getLastBlockSeenHash();
        if (lastSeenBlockHash != null) {
            walletBuilder.setLastSeenBlockHash(hashToByteString(lastSeenBlockHash));
        }
        
        // Populate the lastSeenBlockHeight field.
        int lastSeenBlockHeight = wallet.getLastBlockSeenHeight();
        walletBuilder.setLastSeenBlockHeight(lastSeenBlockHeight);

        // Populate the scrypt parameters.
        KeyCrypter keyCrypter = wallet.getKeyCrypter();
        if (keyCrypter == null) {
            // The wallet is unencrypted.
            walletBuilder.setEncryptionType(EncryptionType.UNENCRYPTED);
        } else {
            // The wallet is encrypted.
            walletBuilder.setEncryptionType(keyCrypter.getUnderstoodEncryptionType());
            if (keyCrypter instanceof KeyCrypterScrypt) {
                walletBuilder.setEncryptionType(EncryptionType.ENCRYPTED_SCRYPT_AES);
                KeyCrypterScrypt keyCrypterScrypt = (KeyCrypterScrypt) keyCrypter;
                walletBuilder.setEncryptionParameters(keyCrypterScrypt.getScryptParameters());
            } else {
                // Some other form of encryption has been specified that we do not know how to persist.
                throw new RuntimeException("The wallet has encryption of type '" + keyCrypter.getUnderstoodEncryptionType() + "' but this WalletProtobufSerializer does not know how to persist this.");
            }
        }

        populateExtensions(wallet, walletBuilder);

        // Populate the wallet version.
        if (wallet.getVersion() != null) {
            walletBuilder.setVersion(wallet.getVersion().getWalletVersionAsInt());
        }

        return walletBuilder.build();
    }

    private static void populateExtensions(Wallet wallet, Protos.Wallet.Builder walletBuilder) {
        for (WalletExtension extension : wallet.getExtensions().values()) {
            Protos.Extension.Builder proto = Protos.Extension.newBuilder();
            proto.setId(extension.getWalletExtensionID());
            proto.setMandatory(extension.isWalletExtensionMandatory());
            proto.setData(ByteString.copyFrom(extension.serializeWalletExtension()));
            walletBuilder.addExtension(proto);
        }
    }

    /**
     * Parses a wallet from the given stream, using the provided Wallet instance to load data into. This is primarily
     * used when you want to register extensions. Data in the proto will be added into the wallet where applicable and
     * overwrite where not.
     */
    public Wallet readWallet(InputStream input) throws IOException {
        Protos.Wallet walletProto = parseToProto(input);
        
        // Read the encryption type to see if the wallet is encrypted or not.
        // If not specified it is unencrypted.
        EncryptionType walletEncryptionType = EncryptionType.UNENCRYPTED;
        
        if (walletProto.hasEncryptionType()) {
            walletEncryptionType = walletProto.getEncryptionType();
        }
        KeyCrypter keyCrypter = null;
        
        if (walletEncryptionType == EncryptionType.ENCRYPTED_SCRYPT_AES) {
            // Read the scrypt parameters that specify how encryption and decryption is performed.
            if (walletProto.hasEncryptionParameters()) {
                Protos.ScryptParameters encryptionParameters = walletProto.getEncryptionParameters();
                keyCrypter = new KeyCrypterScrypt(encryptionParameters);
            }
        }

        NetworkParameters params = NetworkParameters.fromID(walletProto.getNetworkIdentifier());
        Wallet wallet = new Wallet(params, keyCrypter);
        readWallet(walletProto, wallet);
        return wallet;
    }
    
    /**
     * Loads wallet data from the given protocol buffer and inserts it into the given Wallet object. This is primarily
     * useful when you wish to pre-register extension objects. Note that if loading fails the provided Wallet object
     * may be in an indeterminate state and should be thrown away.
     *
     * @throws IOException if there is a problem reading the stream.
     * @throws IllegalArgumentException if the wallet is corrupt.
     */
    public void readWallet(Protos.Wallet walletProto, Wallet wallet) throws IOException {        
        if (walletProto.hasDescription()) {
            wallet.setDescription(walletProto.getDescription());
        }
        
        // Read all keys
        for (Protos.Key keyProto : walletProto.getKeyList()) {
            if (keyProto.getType() != Protos.Key.Type.ORIGINAL) {
                throw new IllegalArgumentException("Unknown key type in wallet");
            }

            byte[] privKey = keyProto.hasPrivateKey() ? keyProto.getPrivateKey().toByteArray() : null;
            EncryptedPrivateKey encryptedPrivateKey = null;
            if (keyProto.hasEncryptedPrivateKey()) {
                Protos.EncryptedPrivateKey encryptedPrivateKeyProto = keyProto.getEncryptedPrivateKey();
                encryptedPrivateKey = new EncryptedPrivateKey(encryptedPrivateKeyProto.getInitialisationVector().toByteArray(),
                        encryptedPrivateKeyProto.getEncryptedPrivateKey().toByteArray());
            }

            byte[] pubKey = keyProto.hasPublicKey() ? keyProto.getPublicKey().toByteArray() : null;

            ECKey ecKey = null;
            final KeyCrypter keyCrypter = wallet.getKeyCrypter();
            if (keyCrypter != null && keyCrypter.getUnderstoodEncryptionType() != EncryptionType.UNENCRYPTED) {
                // If the key is encrypted construct an ECKey using the encrypted private key bytes.
                ecKey = new ECKey(encryptedPrivateKey, pubKey, keyCrypter);
            } else {
                // Construct an unencrypted private key.
                ecKey = new ECKey(privKey, pubKey);           
            }
            ecKey.setCreationTimeSeconds((keyProto.getCreationTimestamp() + 500) / 1000);
            wallet.addKey(ecKey);
        }

        // Read all transactions and insert into the txMap.
        for (Protos.Transaction txProto : walletProto.getTransactionList()) {
            readTransaction(txProto, wallet.getParams());
        }

        // Update transaction outputs to point to inputs that spend them
        for (Protos.Transaction txProto : walletProto.getTransactionList()) {
            WalletTransaction wtx = connectTransactionOutputs(txProto);
            wallet.addWalletTransaction(wtx);
        }

        // Update the lastBlockSeenHash.
        if (!walletProto.hasLastSeenBlockHash()) {
            wallet.setLastBlockSeenHash(null);
        } else {
            wallet.setLastBlockSeenHash(byteStringToHash(walletProto.getLastSeenBlockHash()));
        }
        
        if (!walletProto.hasLastSeenBlockHeight()) {
            wallet.setLastBlockSeenHeight(-1);
        } else {
            wallet.setLastBlockSeenHeight(walletProto.getLastSeenBlockHeight());
        }

        loadExtensions(wallet, walletProto);

        if (walletProto.hasVersion()) {
            int version = walletProto.getVersion();
            if (version == MultiBitWalletVersion.PROTOBUF.getWalletVersionAsInt()) {
                wallet.setVersion(MultiBitWalletVersion.PROTOBUF);
            } else {
                if (version == MultiBitWalletVersion.PROTOBUF_ENCRYPTED.getWalletVersionAsInt()) {
                    wallet.setVersion(MultiBitWalletVersion.PROTOBUF_ENCRYPTED);  
                } else {
                    // Something from the future.
                    throw new WalletVersionException("Did not understand wallet version of '" + version + "'");
                }
            }
        } else {
            // Grandfather in as protobuf.2
            wallet.setVersion(MultiBitWalletVersion.PROTOBUF);
        }

        // Make sure the object can be re-used to read another wallet without corruption.
        txMap.clear();
    }
    
    private static void loadExtensions(Wallet wallet, Protos.Wallet walletProto) {
        final Map<String, WalletExtension> extensions = wallet.getExtensions();
        for (Protos.Extension extProto : walletProto.getExtensionList()) {
            String id = extProto.getId();
            WalletExtension extension = extensions.get(id);
            if (extension == null) {
                if (extProto.getMandatory()) {
                    // If the extension is the ORG_MULTIBIT_WALLET_PROTECT or ORG_MULTIBIT_WALLET_PROTECT_2 then we know about that.
                    // This is a marker extension to prevent earlier versions of multibit loading encrypted wallets.
                    
                    // Unfortunately I merged the recognition of the ORG_MULTIBIT_WALLET_PROTECT mandatory extension into the v0.4 code
                    // so it could load encrypted wallets mistakenly.
                    
                    // Hence the v0.5 code now writes ORG_MULTIBIT_WALLET_PROTECT_2.
                    if (!(extProto.getId().equals(MultiBitWalletProtobufSerializer.ORG_MULTIBIT_WALLET_PROTECT) || 
                            extProto.getId().equals(MultiBitWalletProtobufSerializer.ORG_MULTIBIT_WALLET_PROTECT_2))) {
                        throw new IllegalArgumentException("Did not understand a mandatory extension in the wallet of '" + extProto.getId() + "'");
                    }
                }
            } else {
                log.info("Loading wallet extension {}", id);
                extension.deserializeWalletExtension(extProto.getData().toByteArray());
            }
        }
    }

    protected void readTransaction(Protos.Transaction txProto, NetworkParameters params) {
        Transaction tx = new Transaction(params);
        if (txProto.hasUpdatedAt()) {
            tx.setUpdateTime(new Date(txProto.getUpdatedAt()));
        }
        
        for (Protos.TransactionOutput outputProto : txProto.getTransactionOutputList()) {
            BigInteger value = BigInteger.valueOf(outputProto.getValue());
            byte[] scriptBytes = outputProto.getScriptBytes().toByteArray();
            TransactionOutput output = new TransactionOutput(params, tx, value, scriptBytes);
            tx.addOutput(output);
        }

        for (Protos.TransactionInput transactionInput : txProto.getTransactionInputList()) {
            byte[] scriptBytes = transactionInput.getScriptBytes().toByteArray();
            TransactionOutPoint outpoint = new TransactionOutPoint(params,
                    transactionInput.getTransactionOutPointIndex(),
                    byteStringToHash(transactionInput.getTransactionOutPointHash())
            );
            TransactionInput input = new TransactionInput(params, tx, scriptBytes, outpoint);
            if (transactionInput.hasSequence()) {
                input.setSequenceNumber(transactionInput.getSequence());
            }
            tx.addInput(input);
        }

        for (ByteString blockHash : txProto.getBlockHashList()) {
            tx.addBlockAppearance(byteStringToHash(blockHash));
        }

        if (txProto.hasLockTime()) {
            tx.setLockTime(txProto.getLockTime());
        }

        // Transaction should now be complete.
        Sha256Hash protoHash = byteStringToHash(txProto.getHash());
        Preconditions.checkState(tx.getHash().equals(protoHash),
                "Transaction did not deserialize completely: %s vs %s", tx.getHash(), protoHash);

        // If it is a duplicate, keep the newer.
        // (This code is is here because some old MultiBit serialised wallets had the same tx appearing twice and the wallets would not load).
        if (txMap.containsKey(txProto.getHash())) {
            Transaction txExisting = txMap.get(txProto.getHash());
            if (txExisting.getUpdateTime().after(new Date(txProto.getUpdatedAt()))) {
                // Existing transaction is newer. Keep it.
                log.debug("Wallet contained duplicate transaction %s, keeping the first and newer one", byteStringToHash(txProto.getHash()));
                return;
            } else {
                log.debug("Wallet contained duplicate transaction %s, using the second and newer one", byteStringToHash(txProto.getHash()));
            }
        }
        txMap.put(txProto.getHash(), tx);
    }
    
    /**
     * Formats the given Wallet to the given output stream in protocol buffer format.
     * Add a mandatory extension so that it will not be loaded by older versions.
     */
//    public void writeWalletWithMandatoryExtension(Wallet wallet, OutputStream output) throws IOException {
//        Protos.Wallet walletProto = walletToProto(wallet);
//        Protos.Wallet.Builder walletBuilder = Protos.Wallet.newBuilder(walletProto);
//        Protos.Extension.Builder extensionBuilder = Protos.Extension.newBuilder().setId(ORG_MULTIBIT_WALLET_PROTECT_2).setData(ByteString.copyFrom(new byte[0x01])).setMandatory(true);
//        walletBuilder.addExtension(extensionBuilder);
//
//        Protos.Wallet walletProtoWithMandatory = walletBuilder.build();
//        walletProtoWithMandatory.writeTo(output);
//    }
    
    protected WalletTransaction connectTransactionOutputs(org.bitcoinj.wallet.Protos.Transaction txProto) {
        Transaction tx = txMap.get(txProto.getHash());
        WalletTransaction.Pool pool = WalletTransaction.Pool.valueOf(txProto.getPool().getNumber());
        for (int i = 0 ; i < tx.getOutputs().size() ; i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            final Protos.TransactionOutput transactionOutput = txProto.getTransactionOutput(i);
            if (transactionOutput.hasSpentByTransactionHash()) {
                Transaction spendingTx = txMap.get(transactionOutput.getSpentByTransactionHash());
                final int spendingIndex = transactionOutput.getSpentByTransactionIndex();
                if (spendingTx != null ) {
                    TransactionInput input = spendingTx.getInputs().get(spendingIndex);
                    input.connect(output);
                }
            }
        }
        
        if (txProto.hasConfidence()) {
            Protos.TransactionConfidence confidenceProto = txProto.getConfidence();
            TransactionConfidence confidence = tx.getConfidence();
            readConfidence(tx, confidenceProto, confidence);
        }

        return new WalletTransaction(pool, tx);
    }
    
    private void readConfidence(Transaction tx, Protos.TransactionConfidence confidenceProto,
                                TransactionConfidence confidence) {
        // We are lenient here because tx confidence is not an essential part of the wallet.
        // If the tx has an unknown type of confidence, ignore.
        if (!confidenceProto.hasType()) {
            log.warn("Unknown confidence type for tx {}", tx.getHashAsString());
            return;
        }
        ConfidenceType confidenceType;
        switch (confidenceProto.getType()) {
            case BUILDING: confidenceType = ConfidenceType.BUILDING; break;
            case DEAD: confidenceType = ConfidenceType.DEAD; break;
            // These two are equivalent (must be able to read old wallets).
            case NOT_IN_BEST_CHAIN: confidenceType = ConfidenceType.PENDING; break;
            case NOT_SEEN_IN_CHAIN: confidenceType = ConfidenceType.PENDING; break;
            case UNKNOWN:
                // Fall through.
            default:
                confidenceType = ConfidenceType.UNKNOWN; break;
        }
        confidence.setConfidenceType(confidenceType);
        if (confidenceProto.hasAppearedAtHeight()) {
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING) {
                log.warn("Have appearedAtHeight but not BUILDING for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setAppearedAtChainHeight(confidenceProto.getAppearedAtHeight());
        }
        if (confidenceProto.hasDepth()) {
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING) {
                log.warn("Have depth but not BUILDING for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setDepthInBlocks(confidenceProto.getDepth());
        }
        if (confidenceProto.hasWorkDone()) {
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING) {
                log.warn("Have workDone but not BUILDING for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setWorkDone(BigInteger.valueOf(confidenceProto.getWorkDone()));
        }
        if (confidenceProto.hasOverridingTransaction()) {
            if (confidence.getConfidenceType() != ConfidenceType.DEAD) {
                log.warn("Have overridingTransaction but not OVERRIDDEN for tx {}", tx.getHashAsString());
                return;
            }
            Transaction overridingTransaction =
                txMap.get(confidenceProto.getOverridingTransaction());
            if (overridingTransaction == null) {
                log.warn("Have overridingTransaction that is not in wallet for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setOverridingTransaction(overridingTransaction);
        }
        for (Protos.PeerAddress proto : confidenceProto.getBroadcastByList()) {
            InetAddress ip;
            try {
                ip = InetAddress.getByAddress(proto.getIpAddress().toByteArray());
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);   // IP address is of invalid length.
            }
            int port = proto.getPort();
            PeerAddress address = new PeerAddress(ip, port);
            address.setServices(BigInteger.valueOf(proto.getServices()));
            confidence.markBroadcastBy(address);
        }
        switch (confidenceProto.getSource()) {
            case SOURCE_SELF: confidence.setSource(TransactionConfidence.Source.SELF); break;
            case SOURCE_NETWORK: confidence.setSource(TransactionConfidence.Source.NETWORK); break;
            case SOURCE_UNKNOWN:
                // Fall through.
            default: confidence.setSource(TransactionConfidence.Source.UNKNOWN); break;
        }
    }
}
package me.youngjibbit.core.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ByteProcessor;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.NettyCompressionEncoder;
import net.minecraft.network.PacketBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** Wire bytes, shared indices and ownership across the Netty upgrade; no server or GL context. */
public class PacketBufferNettyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void minecraftWireBytesAndUtf8RemainBigEndian() {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeVarIntToBuffer(300);
            packet.writeShort(0x1234);
            packet.writeInt(0x12345678);
            packet.writeLong(0x0102030405060708L);
            packet.writeString("Gr\u00fc\u00dfe");
            byte[] actual = new byte[packet.readableBytes()];
            packet.getBytes(0, actual);
            assertArrayEquals(new byte[]{(byte)0xac, 2, 0x12, 0x34, 0x12, 0x34, 0x56, 0x78,
                    1, 2, 3, 4, 5, 6, 7, 8, 7, 0x47, 0x72, (byte)0xc3, (byte)0xbc,
                    (byte)0xc3, (byte)0x9f, 0x65}, actual);
            assertEquals(300, packet.readVarIntFromBuffer());
            assertEquals(0x1234, packet.readUnsignedShort());
            assertEquals(0x12345678, packet.readInt());
            assertEquals(0x0102030405060708L, packet.readLong());
            assertEquals("Gr\u00fc\u00dfe", packet.readStringFromBuffer(20));
            assertFalse(packet.isReadable());
        } finally { packet.release(); }
    }

    @Test public void utf8StringsReadFromHeapDirectPooledAndReadOnlySlices() {
        String expected = "Gr\u00fc\u00dfe \ud83d\ude03";
        byte[] utf8 = expected.getBytes(StandardCharsets.UTF_8);
        for (boolean pooled : new boolean[]{false, true}) {
            for (boolean direct : new boolean[]{false, true}) {
                for (boolean readOnly : new boolean[]{false, true}) {
                    ByteBuf backing = pooled
                            ? (direct ? PooledByteBufAllocator.DEFAULT.directBuffer(64)
                                      : PooledByteBufAllocator.DEFAULT.heapBuffer(64))
                            : (direct ? Unpooled.directBuffer(64) : Unpooled.buffer(64));
                    try {
                        backing.writeShort(0xcafe);
                        backing.writeByte(utf8.length);
                        backing.writeBytes(utf8);
                        backing.writeByte(0x5a);
                        ByteBuf view = backing.slice(2, utf8.length + 2);
                        if (readOnly) view = view.asReadOnly();
                        PacketBuffer packet = new PacketBuffer(view);
                        int references = backing.refCnt();
                        assertEquals(direct, packet.isDirect());
                        assertEquals(readOnly, packet.isReadOnly());
                        assertEquals(expected, packet.readStringFromBuffer(expected.length()));
                        assertEquals(utf8.length + 1, packet.readerIndex());
                        assertEquals(utf8.length + 2, packet.writerIndex());
                        assertEquals(0x5a, packet.readUnsignedByte());
                        assertFalse(packet.isReadable());
                        assertEquals("decoding must not retain or release the input", references, backing.refCnt());
                        assertEquals("slice cursor must not consume the parent", 0, backing.readerIndex());
                    } finally { backing.release(); }
                    assertEquals(0, backing.refCnt());
                }
            }
        }
    }

    @Test public void utf8StringMaySpanDirectCompositeComponents() {
        byte[] first = {7, 0x47, 0x72, (byte) 0xc3};
        byte[] second = {(byte) 0xbc, (byte) 0xc3, (byte) 0x9f, 0x65, 0x5a};
        ByteBuf firstPart = Unpooled.directBuffer(first.length).writeBytes(first);
        ByteBuf secondPart = Unpooled.directBuffer(second.length).writeBytes(second);
        CompositeByteBuf composite = Unpooled.compositeBuffer();
        try {
            composite.addComponents(true, firstPart, secondPart);
            PacketBuffer packet = new PacketBuffer(composite.asReadOnly());
            assertFalse(packet.hasArray());
            assertEquals("Gr\u00fc\u00dfe", packet.readStringFromBuffer(5));
            assertEquals(8, packet.readerIndex());
            assertEquals(0x5a, packet.readUnsignedByte());
            assertEquals(1, firstPart.refCnt());
            assertEquals(1, secondPart.refCnt());
        } finally { composite.release(); }
        assertEquals(0, firstPart.refCnt());
        assertEquals(0, secondPart.refCnt());
    }

    @Test public void truncatedStringCannotReadInitializedBytesBeyondWriterIndex() {
        ByteBuf backing = Unpooled.directBuffer(32);
        try {
            backing.writeByte(4);
            backing.writeBytes(new byte[]{'a', 'b'});
            backing.setByte(3, 'c');
            backing.setByte(4, 'd');
            PacketBuffer packet = new PacketBuffer(backing.asReadOnly());
            try { packet.readStringFromBuffer(4); fail("read beyond the received payload"); }
            catch (IndexOutOfBoundsException expected) { }
            assertEquals("only the length prefix may be consumed", 1, packet.readerIndex());
            assertEquals(3, packet.writerIndex());
            assertEquals('a', packet.readUnsignedByte());
            assertEquals(1, backing.refCnt());
        } finally { backing.release(); }
    }

    @Test public void stringLengthGuardsRemainEffectiveForDirectBuffers() {
        for (int invalidLength : new int[]{-1, 5}) {
            PacketBuffer packet = new PacketBuffer(Unpooled.directBuffer(32));
            try {
                packet.writeVarIntToBuffer(invalidLength);
                packet.writeByte(0x5a);
                int prefix = PacketBuffer.getVarIntSize(invalidLength);
                try { packet.readStringFromBuffer(1); fail("invalid encoded length accepted"); }
                catch (io.netty.handler.codec.DecoderException expected) { }
                assertEquals(prefix, packet.readerIndex());
                assertEquals(0x5a, packet.readUnsignedByte());
                assertEquals(1, packet.refCnt());
            } finally { packet.release(); }
        }
        PacketBuffer packet = new PacketBuffer(Unpooled.directBuffer(32));
        try {
            packet.writeBytes(new byte[]{2, 'a', 'b', 0x5a});
            try { packet.readStringFromBuffer(1); fail("decoded character limit ignored"); }
            catch (io.netty.handler.codec.DecoderException expected) { }
            assertEquals(3, packet.readerIndex());
            assertEquals(0x5a, packet.readUnsignedByte());
        } finally { packet.release(); }
    }

    @Test public void explicitLittleEndianOperationsPreserveBytesAndIndicesForBothOrders() {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer().order(order));
            try {
                packet.writeShortLE(0xfedc);
                packet.writeMediumLE(0xfedcba);
                packet.writeIntLE(0xfedcba98);
                packet.writeLongLE(0x0102030405060708L);
                byte[] bytes = new byte[17];
                packet.getBytes(0, bytes);
                assertArrayEquals(new byte[]{(byte)0xdc, (byte)0xfe, (byte)0xba, (byte)0xdc, (byte)0xfe,
                        (byte)0x98, (byte)0xba, (byte)0xdc, (byte)0xfe, 8, 7, 6, 5, 4, 3, 2, 1}, bytes);
                assertEquals(0xfedc, packet.getUnsignedShortLE(0));
                assertEquals((short)0xfedc, packet.getShortLE(0));
                assertEquals(0xfedcba, packet.getUnsignedMediumLE(2));
                assertEquals(-74566, packet.getMediumLE(2));
                assertEquals(0xfedcba98L, packet.getUnsignedIntLE(5));
                assertEquals(0xfedcba98, packet.getIntLE(5));
                assertEquals(0x0102030405060708L, packet.getLongLE(9));
                assertEquals(0, packet.readerIndex());
                assertEquals((short)0xfedc, packet.readShortLE());
                assertEquals(-74566, packet.readMediumLE());
                assertEquals(0xfedcba98, packet.readIntLE());
                assertEquals(0x0102030405060708L, packet.readLongLE());
                packet.readerIndex(0);
                assertEquals(0xfedc, packet.readUnsignedShortLE());
                assertEquals(0xfedcba, packet.readUnsignedMediumLE());
                assertEquals(0xfedcba98L, packet.readUnsignedIntLE());
                packet.setShortLE(0, 0x1234);
                packet.setMediumLE(2, 0x56789a);
                packet.setIntLE(5, 0x12345678);
                packet.setLongLE(9, 0x1122334455667788L);
                assertEquals(9, packet.readerIndex());
                assertEquals(17, packet.writerIndex());
                assertEquals(0x1234, packet.getUnsignedShortLE(0));
                assertEquals(0x56789a, packet.getUnsignedMediumLE(2));
                assertEquals(0x12345678, packet.getIntLE(5));
                assertEquals(0x1122334455667788L, packet.getLongLE(9));
            } finally { packet.release(); }
        }
    }

    @Test public void retainedViewsOwnTheirReferencesAfterParentRelease() {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        ByteBuf slice = null, range = null, all = null, duplicate = null;
        try {
            packet.writeInt(0x12345678);
            packet.writeInt(0x76543210);
            packet.touch().touch("packet-owner");
            assertEquals(1, packet.refCnt());
            slice = packet.readRetainedSlice(4);
            range = packet.retainedSlice(4, 4);
            all = packet.retainedSlice();
            duplicate = packet.retainedDuplicate();
            assertEquals(4, packet.readerIndex());
            assertEquals(5, packet.refCnt());
            assertFalse(packet.release());
            assertEquals(0x12345678, slice.readInt());
            assertEquals(0x76543210, range.readInt());
            assertEquals(0x76543210, all.readInt());
            assertEquals(0x76543210, duplicate.readInt());
        } finally {
            if (slice != null) slice.release();
            if (range != null) range.release();
            if (all != null) all.release();
            if (duplicate != null) duplicate.release();
            if (packet.refCnt() > 0) packet.release();
        }
        assertEquals(0, packet.refCnt());
    }

    @Test public void readOnlyViewsAndUtf8ByteProcessingShareTheWrappedStorage() {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer(32));
        try {
            assertEquals(5, packet.writeCharSequence("caf\u00e9", StandardCharsets.UTF_8));
            assertEquals(0, packet.readerIndex());
            assertEquals("caf\u00e9", packet.getCharSequence(0, 5, StandardCharsets.UTF_8).toString());
            assertEquals("caf\u00e9", packet.readCharSequence(5, StandardCharsets.UTF_8).toString());
            assertEquals(5, packet.readerIndex());
            assertEquals(2, packet.setCharSequence(0, "ok", StandardCharsets.UTF_8));
            assertEquals(5, packet.writerIndex());
            ByteBuf readOnly = packet.asReadOnly();
            assertTrue(readOnly.isReadOnly());
            assertFalse(packet.isReadOnly());
            assertEquals('o', readOnly.getByte(0));
            try { readOnly.setByte(0, 0); fail("read-only view accepted a write"); }
            catch (java.nio.ReadOnlyBufferException expected) { }
            packet.clear();
            packet.writeBytes(new byte[]{1, 2, 0, 3});
            assertEquals(2, packet.forEachByte(ByteProcessor.FIND_NUL));
            assertEquals(2, packet.forEachByte(1, 3, ByteProcessor.FIND_NUL));
            assertEquals(2, packet.forEachByteDesc(ByteProcessor.FIND_NUL));
            assertEquals(2, packet.forEachByteDesc(1, 3, ByteProcessor.FIND_NUL));
        } finally { packet.release(); }
    }

    @Test public void positionedFileTransfersRespectBufferAndFileCursors() throws Exception {
        PacketBuffer source = new PacketBuffer(Unpooled.buffer());
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(16));
        try (RandomAccessFile file = new RandomAccessFile(temporary.newFile(), "rw")) {
            FileChannel channel = file.getChannel();
            source.writeLong(0x0102030405060708L);
            channel.position(10);
            assertEquals(8, source.getBytes(0, channel, 2, 8));
            assertEquals(0, source.readerIndex());
            assertEquals(10, channel.position());
            assertEquals(8, copy.setBytes(0, channel, 2, 8));
            assertEquals(0, copy.writerIndex());
            copy.writerIndex(8);
            assertEquals(source.getLong(0), copy.readLong());
            copy.clear();
            assertEquals(8, copy.writeBytes(channel, 2, 8));
            assertEquals(8, copy.writerIndex());
            assertEquals(8, source.readBytes(channel, 12, 8));
            assertEquals(8, source.readerIndex());
            assertEquals(10, channel.position());
            assertEquals(source.getLong(0), copy.readLong());
        } finally { source.release(); copy.release(); }
    }

    @Test public void compressionRoundTripsBelowAndAboveThresholdWithoutLeakedBuffers() {
        for (int size : new int[]{31, 256, 4096}) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++) bytes[i] = (byte)(i * 37);
            EmbeddedChannel encoder = new EmbeddedChannel(new NettyCompressionEncoder(256));
            EmbeddedChannel decoder = new EmbeddedChannel(new NettyCompressionDecoder(256));
            try {
                assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(bytes)));
                ByteBuf encoded = encoder.readOutbound();
                assertNotNull(encoded);
                assertTrue(decoder.writeInbound(encoded));
                ByteBuf decoded = decoder.readInbound();
                try {
                    byte[] actual = new byte[decoded.readableBytes()];
                    decoded.readBytes(actual);
                    assertArrayEquals(bytes, actual);
                } finally { decoded.release(); }
            } finally {
                encoder.finishAndReleaseAll();
                decoder.finishAndReleaseAll();
            }
        }
    }
}

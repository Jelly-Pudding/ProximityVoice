package com.jellypudding.proximityVoice.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Binary serialisation matching Minecraft's FriendlyByteBuf wire format.
 * Primitives are big-endian. Strings and byte arrays use a VarInt length prefix.
 * UUIDs are two big-endian longs (MSB then LSB).
 */
public class PacketBuffer {

    private ByteArrayOutputStream writeTarget;
    private DataOutputStream writer;
    private DataInputStream reader;

    public PacketBuffer() {
        writeTarget = new ByteArrayOutputStream();
        writer = new DataOutputStream(writeTarget);
    }

    public PacketBuffer(byte[] data) {
        reader = new DataInputStream(new ByteArrayInputStream(data));
    }

    public byte[] toBytes() { return writeTarget.toByteArray(); }

    public void writeByte(int b) throws IOException { writer.writeByte(b); }
    public void writeInt(int v) throws IOException { writer.writeInt(v); }
    public void writeLong(long v) throws IOException { writer.writeLong(v); }
    public void writeFloat(float v) throws IOException { writer.writeFloat(v); }
    public void writeDouble(double v) throws IOException { writer.writeDouble(v); }
    public void writeBoolean(boolean v) throws IOException { writer.writeBoolean(v); }

    public void writeUUID(UUID uuid) throws IOException {
        writer.writeLong(uuid.getMostSignificantBits());
        writer.writeLong(uuid.getLeastSignificantBits());
    }

    public void writeVarInt(int v) throws IOException {
        while (true) {
            if ((v & ~0x7F) == 0) { writer.writeByte(v); return; }
            writer.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
    }

    public void writeBytes(byte[] data) throws IOException { writer.write(data); }

    public void writeByteArray(byte[] data) throws IOException {
        writeVarInt(data.length);
        writer.write(data);
    }

    public void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        writer.write(bytes);
    }

    public int readByte() throws IOException { return reader.readByte() & 0xFF; }
    public int readInt() throws IOException { return reader.readInt(); }
    public long readLong() throws IOException { return reader.readLong(); }
    public float readFloat() throws IOException { return reader.readFloat(); }
    public double readDouble() throws IOException { return reader.readDouble(); }
    public boolean readBoolean() throws IOException { return reader.readBoolean(); }

    public UUID readUUID() throws IOException {
        return new UUID(reader.readLong(), reader.readLong());
    }

    public int readVarInt() throws IOException {
        int result = 0, shift = 0;
        byte b;
        do {
            b = reader.readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too long");
        } while ((b & 0x80) != 0);
        return result;
    }

    public byte[] readBytes(int n) throws IOException {
        byte[] data = new byte[n];
        reader.readFully(data);
        return data;
    }

    public byte[] readByteArray() throws IOException {
        byte[] data = new byte[readVarInt()];
        reader.readFully(data);
        return data;
    }

    public String readString() throws IOException {
        byte[] bytes = new byte[readVarInt()];
        reader.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

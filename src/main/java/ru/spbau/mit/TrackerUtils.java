package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class TrackerUtils {

    private static final Logger LOGGER = Logger.getLogger("TrackerUtils");

    private TrackerUtils() {
    }

    // executeList request
    public static void executeList(DataOutputStream output, TrackerState state) throws IOException {
        state.listFiles(output);
    }

    // executeUpload request
    public static void executeUpload(
            Socket clientSocket, DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        String name = input.readUTF();
        long size = input.readLong();
        int id = state.getId();
        FileDescr fd = new FileDescr(id, name, size);
        state.getFiles().add(fd);
//        int id;
//        FileDescr fd;
//        synchronized (state.filesSync) {
//            id = state.getFiles().size();
//            fd = new FileDescr(id, name, size);
//            state.getFiles().add(fd);
//        }

        LOGGER.info("Upload handler -- read data: name=" + name + " size=" + Long.toString(size));
        // how to distinguish one client from another if they all seem to have the same address?
        InetAddress clientAddr = clientSocket.getInetAddress();
        LOGGER.warning("EXECUTE_UPLOAD: clientSocket.address=" + clientAddr.toString());
        int port = clientSocket.getPort();
        LOGGER.warning("EXECUTE_UPLOAD: clientSocket.port=" + Integer.toString(port));
        ClientDescriptor client = state.getClients().get(clientAddr);
        if (client != null) {
            client.setLastUpdated(System.currentTimeMillis());
        } else {
            client = new ClientDescriptor(clientAddr, port, System.currentTimeMillis());
            state.getClients().put(clientAddr, client);
        }

        ArrayList<ClientDescriptor> tmp = new ArrayList<>();
        tmp.add(client);
        state.getSeeds().put(fd.getId(), tmp);

        output.writeInt(id);
        output.flush();
    }

    // executeSources request
    public static void executeSources(DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        int id = input.readInt();
        state.updateSeeds(id);
        List<ClientDescriptor> fileSeeds = state.getSeeds().get(id);
        if (fileSeeds == null) {
            LOGGER.warning("Wrong file id!");
            return;
        }
        output.writeInt(fileSeeds.size());
        for (ClientDescriptor seed : fileSeeds) {
            byte[] addrBytes = seed.getAddr().getAddress();
            output.write(addrBytes);
            // output.writeShort(TorrentTrackerMain.PORT);
            output.writeShort(seed.getPort());
        }
        output.flush();
    }

    // executeUpdate request
    public static void executeUpdate(
            Socket clientSocket, DataInputStream input, DataOutputStream output, TrackerState state)
            throws IOException {
        try {
            int seedPort = input.readInt();
            int count = input.readInt();
            for (int i = 0; i < count; i++) {
                int id = input.readInt();
                boolean updated = false;
                for (ClientDescriptor seed : state.getSeeds().get(id)) {
                    if (seed.getAddr() == clientSocket.getInetAddress()) {
                        seed.setLastUpdated(System.currentTimeMillis());
                        updated = true;
                    }
                }
                if (!updated) {
                    InetAddress addr = clientSocket.getInetAddress();
                    ClientDescriptor seed = state.getClients().get(addr); //.get(seed_port);
                    if (seed == null) {
                        seed = new ClientDescriptor(addr, seedPort, System.currentTimeMillis());
                        state.getClients().put(addr, seed); //.get(addr).put(seed_port, seed);
                    } else {
                        seed.setLastUpdated(System.currentTimeMillis());
                    }
                    state.getSeeds().get(id).add(seed);
                }
            }
        } catch (IOException e) {
            output.writeBoolean(false);
            output.flush();
            return;
        }
        output.writeBoolean(true);
        output.flush();
    }
}

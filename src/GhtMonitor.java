public class GhtMonitor {
    public static void main(String[] args) {
        String latestFile_1;
        while (true) {
            latestFile_1 = getLatestFile();
            try {
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String latestFile_2 = getLatestFile();
            if (latestFile_1.equals(latestFile_2)) {
                new IO_Process().exeCmd("python ./scratchpad.py -i repoList.txt".split(" "), "/home/feature/shuruiz/ForkData/ghd/");
            }
        }
    }

    public static String getLatestFile() {
        String[] cmd_getLatestFile = {"/bin/sh",
                "-c",
                "ls -rt | tail --lines=1"};

        String latestFile = new IO_Process().exeCmd(cmd_getLatestFile, "/home/feature/shuruiz/ForkData/shurui.cache");
        return latestFile;
    }
}

package sir.sukhov.smovefile;

class Candidate {

    private int score;
    private long size;
    private int mtime;

    Candidate(long size, int mtime) {
        this.size = size;
        this.mtime = mtime;
        this.score = 1;
    }

    int getScore() {
        return score;
    }

    long getSize() {
        return size;
    }

    int getMtime() {
        return mtime;
    }

    void incScore() {
        this.score++;
    }
    void clearScore(long size, int mtime) {
        this.size = size;
        this.mtime = mtime;
        this.score = 1;
    }
}

package dev.chunkfinder.core;

/** Conservative face-exposure test. Unknown neighbors are never treated as air. */
public final class ExposedFaces {
    public interface World {
        int minY();
        int maxY(); // exclusive
        boolean loaded(int chunkX, int chunkZ);
        boolean open(int x, int y, int z);
    }
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private ExposedFaces() { }

    public static boolean test(World world, int x, int y, int z) {
        for (int[] face : FACES) {
            long nx = (long) x + face[0], ny = (long) y + face[1], nz = (long) z + face[2];
            if (nx < Integer.MIN_VALUE || nx > Integer.MAX_VALUE || nz < Integer.MIN_VALUE || nz > Integer.MAX_VALUE
                    || ny < world.minY() || ny >= world.maxY()) continue;
            int px = (int) nx, py = (int) ny, pz = (int) nz;
            if (world.loaded(px >> 4, pz >> 4) && world.open(px, py, pz)) return true;
        }
        return false;
    }
}

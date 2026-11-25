package cn.kigarface.prototype;

/** Read the original tracker's optional complexion and fringe result without copying camera pixels. */
public final class FaceSurfaceSamples {
    private FaceSurfaceSamples() {}

    public record Surface(int color, float x, float y, float dx, float dy, float height, float[] fringe) {}

    public static java.util.Map<Long, Surface> read(KigFaceTracker.Frame frame) {
        var samples = frame.result.complexions();
        if (samples.isEmpty()) return java.util.Map.of();
        var result = new java.util.HashMap<Long, Surface>();
        for (var track : frame.result.faces()) {
            var skin = Complexion.matching(track.detection(), samples);
            if (skin == null) continue;
            var geometry = Complexion.geometry(track.detection());
            if (geometry == null) continue;
            var fringe = skin.fringe();
            result.put(track.id(), new Surface(skin.color(), geometry.x(), geometry.y(), geometry.dx(),
                geometry.dy(), geometry.height(), fringe == null ? null : fringe.clone()));
        }
        return result;
    }
}

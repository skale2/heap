package Objects;


public class NULL extends Any {
    private static NULL ourInstance = new NULL();

    public static NULL getInstance() {
        return ourInstance;
    }

    private NULL() {}
}

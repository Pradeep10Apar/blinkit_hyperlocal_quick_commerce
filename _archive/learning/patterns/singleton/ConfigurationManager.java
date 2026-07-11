import java.util.HashMap;
import java.util.Map;

/**
 * EXERCISE: Implement a Singleton Configuration Manager
 * 
 * REQUIREMENTS:
 * 1. Only ONE instance should ever exist
 * 2. Use EAGER initialization (create instance when class loads)
 * 3. Store configuration as key-value pairs in a HashMap
 * 
 * HINTS:
 * - Private constructor
 * - Static final instance
 * - Public static getInstance() method
 */
public class ConfigurationManager {

    // ============================================================
    // TODO 1: Create a private static final instance of this class
    //         Name it: INSTANCE
    //         Initialize it directly (eager initialization)
    // ============================================================
    private static ConfigurationManager instance;

    // ============================================================
    // TODO 2: Create a private Map<String, String> to store properties
    //         Name it: properties
    // ============================================================
    private Map<String, String> properties = new HashMap<>();

    // ============================================================
    // TODO 3: Create a PRIVATE constructor
    //         Inside the constructor:
    //         - Initialize the properties HashMap
    //         - Add these default values:
    //           "app.name"     → "BlinkitApp"
    //           "app.version"  → "1.0.0"
    //           "database.url" → "localhost:5432"
    //           "database.name"→ "blinkit_db"
    //         - Print: "ConfigurationManager initialized!"
    // ============================================================
    private ConfigurationManager(){
        System.out.println("ConfigurationManager instance created!"); 
        properties.put("app.name","BlinkitApp");
        properties.put("app.version","1.0.0");
        properties.put("database.url","localhost:5432");
        properties.put("database.name","blinkit_db");      

    }

    // ============================================================
    // TODO 4: Create a public static getInstance() method
    //         - Returns the INSTANCE
    // ============================================================
    public static ConfigurationManager getInstance(){
        if(instance==null){

            synchronized(ConfigurationManager.class){
                if(instance==null){
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }

    // ============================================================
    // TODO 5: Create a public getProperty(String key) method
    //         - Returns the value for the given key
    //         - Returns null if key doesn't exist
    // ============================================================
    public String getProperty(String key){
        return properties.get(key) ;

    }

    // ============================================================
    // TODO 6: Create a public setProperty(String key, String value) method
    //         - Adds or updates a property
    // ============================================================
    public void setProperty(String key, String value){
        properties.put(key,value);

    }

    // ============================================================
    // TODO 7 (BONUS): Create a public printAllProperties() method
    //         - Prints all key-value pairs nicely formatted
    // ============================================================
    
}

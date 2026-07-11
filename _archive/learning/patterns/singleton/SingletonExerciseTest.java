/**
 * TEST YOUR SINGLETON IMPLEMENTATION
 * 
 * Run this class after completing ConfigurationManager.
 * All tests should pass (print ✓) if your implementation is correct.
 */
public class SingletonExerciseTest {

    public static void main(String[] args) {
        System.out.println("+------------------------------------------------------------+");
        System.out.println("|          SINGLETON PATTERN - EXERCISE TEST                 |");
        System.out.println("+------------------------------------------------------------+");
        System.out.println();

        int passed = 0;
        int total = 5;

        // Test 1: getInstance() returns non-null
        System.out.println("TEST 1: getInstance() returns non-null instance");
        try {
            ConfigurationManager config = ConfigurationManager.getInstance();
            if (config != null) {
                System.out.println("   [PASSED] Instance is not null\n");
                passed++;
            } else {
                System.out.println("   [FAILED] Instance is null\n");
            }
        } catch (Exception e) {
            System.out.println("   [FAILED] Exception: " + e.getMessage() + "\n");
        }

        // Test 2: Same instance returned every time
        System.out.println("TEST 2: Same instance returned every time (Singleton check)");
        try {
            ConfigurationManager config1 = ConfigurationManager.getInstance();
            ConfigurationManager config2 = ConfigurationManager.getInstance();
            ConfigurationManager config3 = ConfigurationManager.getInstance();
            
            if (config1 == config2 && config2 == config3) {
                System.out.println("   [PASSED] All references point to same instance\n");
                passed++;
            } else {
                System.out.println("   [FAILED] Different instances returned!\n");
            }
        } catch (Exception e) {
            System.out.println("   [FAILED] Exception: " + e.getMessage() + "\n");
        }

        // Test 3: Default properties are set
        System.out.println("TEST 3: Default properties are initialized");
        try {
            ConfigurationManager config = ConfigurationManager.getInstance();
            String appName = config.getProperty("app.name");
            String dbUrl = config.getProperty("database.url");
            
            if (appName != null && dbUrl != null) {
                System.out.println("   [PASSED] Default properties exist");
                System.out.println("     app.name = " + appName);
                System.out.println("     database.url = " + dbUrl + "\n");
                passed++;
            } else {
                System.out.println("   [FAILED] Default properties not found\n");
            }
        } catch (Exception e) {
            System.out.println("   [FAILED] Exception: " + e.getMessage() + "\n");
        }

        // Test 4: Can set and get properties
        System.out.println("TEST 4: Can set and get new properties");
        try {
            ConfigurationManager config = ConfigurationManager.getInstance();
            config.setProperty("test.key", "test.value");
            String value = config.getProperty("test.key");
            
            if ("test.value".equals(value)) {
                System.out.println("   [PASSED] Property set and retrieved correctly\n");
                passed++;
            } else {
                System.out.println("   [FAILED] Retrieved value doesn't match\n");
            }
        } catch (Exception e) {
            System.out.println("   [FAILED] Exception: " + e.getMessage() + "\n");
        }

        // Test 5: Changes visible across all references
        System.out.println("TEST 5: Changes from one reference visible in another");
        try {
            ConfigurationManager config1 = ConfigurationManager.getInstance();
            ConfigurationManager config2 = ConfigurationManager.getInstance();
            
            config1.setProperty("shared.property", "shared.value");
            String valueFromConfig2 = config2.getProperty("shared.property");
            
            if ("shared.value".equals(valueFromConfig2)) {
                System.out.println("   [PASSED] Changes are shared (proves single instance)\n");
                passed++;
            } else {
                System.out.println("   [FAILED] Changes not visible across references\n");
            }
        } catch (Exception e) {
            System.out.println("   [FAILED] Exception: " + e.getMessage() + "\n");
        }

        // Summary
        System.out.println("============================================================");
        System.out.println("RESULTS: " + passed + "/" + total + " tests passed");
        
        if (passed == total) {
            System.out.println();
            System.out.println("*** CONGRATULATIONS! Your Singleton implementation is correct! ***");
            System.out.println("   You've successfully implemented the Singleton pattern.");
            System.out.println();
            System.out.println("BONUS: Try calling printAllProperties() if you implemented it!");
        } else {
            System.out.println();
            System.out.println("Keep trying! Review the TODOs and hints in ConfigurationManager.java");
        }
        System.out.println("============================================================");
    }
}

public class java {

    @RemoteController
    public class HelloController {

        @GetMapping("/hello")
        public String hello() {
            return "Hello from java!";
        }
    }
       
}
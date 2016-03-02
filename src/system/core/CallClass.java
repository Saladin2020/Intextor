/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package system.core;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Saladin2020
 */
public class CallClass {

    private HashMap<String, Class> classList;

    public CallClass() {
        this.classList = new HashMap<String, Class>();
    }

    protected void setup(String className) throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        Class c = Class.forName("intextor.controllers." + className, true, this.getClass().getClassLoader());
        this.classList.put(className, c);

        //Method method = c.getMethod("index", (Class<?>[]) null);//.getMethod("index", (Class<?>[]) null).invoke(c, (Object[]) null);
        // method.invoke(c, (Object[]) null);
    }

    protected void proc(String className) throws InstantiationException, IllegalAccessException {
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    classList.get(className).newInstance();
                } catch (InstantiationException ex) {
                    Logger.getLogger(CallClass.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(CallClass.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        new Thread(runnable).start();
    }

}

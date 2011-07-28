/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.irt.netserv.core.backbone;

/**
 *
 * @author amanus
 */
public class ServiceException extends Exception{
    String service;
    
    ServiceException(){
        service = "unknown";
    }
    
    ServiceException(String s){
        service = s;
    }
    
    @Override
    public String toString(){
        return service;
    }
}

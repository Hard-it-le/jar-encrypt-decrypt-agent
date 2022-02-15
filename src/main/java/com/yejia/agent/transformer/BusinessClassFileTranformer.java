package com.yejia.agent.transformer;
import com.yejia.agent.ClassDecryptUtil;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class BusinessClassFileTranformer implements ClassFileTransformer {
    private String password;
    private String packageName;
    public BusinessClassFileTranformer(String password,String packageName){
        this.password = password;
        this.packageName = packageName;
    }
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if(support(className)){
            try {
                System.out.println(password);
                return ClassDecryptUtil.decrypt(classfileBuffer, password);
            } catch (Exception e) {
            }
        }
        return classfileBuffer;
    }

    private boolean support(String className ){
        if(className.contains(this.packageName)){
            System.out.println(className);
        }

        className = className.substring(0, className.lastIndexOf("/")+1);
        return className.contains(this.packageName)&&!className.contains("/BOOT-INF/lib/common-0.0.1.jar!/");
    }
}

package com.yejia.agent;
import com.yejia.agent.transformer.BusinessClassFileTranformer;
import javassist.*;

import java.lang.instrument.Instrumentation;
import java.util.StringTokenizer;

/**
 * @author yujiale
 */
public class DecryptAgent {
    private static final String CLASS_NAME = "org.springframework.core.type.classreading.SimpleMetadataReader";
    private static final String CLASS_NAME1 = "org.springframework.asm.ClassReader";
    public static void premain(String args, Instrumentation instr){
        if(args == null || args.trim().length() == 0){
            System.out.println("解密参数不能为空");
            System.exit(0);
            return;
        }
        StringTokenizer stringTokenizer = new StringTokenizer(args,",");
        String key = stringTokenizer.nextToken();
        String packageName = stringTokenizer.nextToken();
        instr.addTransformer(new BusinessClassFileTranformer(key,packageName));
        ClassPool classPool = ClassPool.getDefault();
       try{
           classPool.appendClassPath(new LoaderClassPath(classPool.getClass().getClassLoader()));
           modifyLaunchedUrlClassLoader(classPool, key, packageName);
       }catch (Exception e){
           e.printStackTrace();
       }
    }

    public static void modifyLaunchedUrlClassLoader(ClassPool classPool, String key, String packageName) {
        try{
            CtClass ctClass = classPool.get("org.springframework.boot.loader.LaunchedURLClassLoader");
            CtConstructor[] ctConstructors = ctClass.getDeclaredConstructors();
            CtConstructor targetConstructor = null;
            for(CtConstructor ctConstructor: ctConstructors){
                System.out.println("constructor parameter type length is "+ctConstructor.getParameterTypes().length);
                if(ctConstructor.getParameterTypes().length == 4){
                    targetConstructor = ctConstructor;
                    break;
                }
            }
            if(targetConstructor != null){
                targetConstructor.insertAfter("com.yejia.agent.DecryptAgent.modifyClassReaderClass(this,\""+packageName+"\");");
                targetConstructor.insertAfter("com.yejia.agent.DecryptAgent.modifySimpleMetadataReaderClass(this, \""+key+"\");");
                targetConstructor.insertAfter("System.out.println(\""+key+"\");");
            }
           ctClass.toClass(ctClass.getClass().getClassLoader(), ctClass.getClass().getProtectionDomain());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void modifySimpleMetadataReaderClass(ClassLoader loader, String key) {
        try {
            ClassPool classPool = ClassPool.getDefault();
            classPool.appendClassPath(new LoaderClassPath(loader));
            CtClass ctClass = classPool.get(CLASS_NAME);
            CtMethod ctMethod = ctClass.getDeclaredMethod("getClassReader");
            //修改方法
            String methodBody = "{java.io.InputStream is = null;" +
                    " try {" +
                    "is = $1.getInputStream();"+
                    "java.net.URL url = $1.getURL();"+
//                    "System.out.println(\"file path is \"+url.getPath());"+
                    "String path = url.getPath().substring(url.getPath().indexOf(\"/BOOT-INF\"));"+
                    "String fileName = path.substring(path.lastIndexOf(\"/\")+1);"+
                    "return new org.springframework.asm.ClassReader(\""+key+"\",path.replace(fileName,\"\"),url.getPath(), is);" +
                    "}" +
                    "catch (Exception ex) {" +
//                    "System.out.println(\"error is \"+ex);"+
                    "throw new org.springframework.core.NestedIOException(\"ASM ClassReader failed to parse class file -" +
                    "probably due to a new Java class file version that isn't supported yet: \" + $1, ex);" +
                    "}finally{if(is!=null){ is.close();} }" +
                    "}";
            ctMethod.setBody(methodBody);
            ctClass.toClass(loader, ctClass.getClass().getProtectionDomain());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void modifyClassReaderClass(ClassLoader loader, String packageName) {
        try {
            ClassPool classPool = ClassPool.getDefault();
            classPool.appendClassPath(new LoaderClassPath(loader));
            CtClass ctClass = classPool.get(CLASS_NAME1);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("private static byte[] readStream(String key,String name,String path,java.io.InputStream inputStream,boolean close) throws Exception");
            stringBuffer.append("{");
            stringBuffer.append("byte [] bytes = readStream(inputStream, close);");
            stringBuffer.append("boolean r = name.contains(\"/BOOT-INF/classes!/"+packageName+"\")&&!name.contains(\"common-0.0.1.jar\");");
//            stringBuffer.append("System.out.println(name+\"   \"+r+\" \"+path);");
            stringBuffer.append("if(r){");
            stringBuffer.append("bytes = com.yejia.agent.ClassDecryptUtil.decrypt(bytes, key);");
            stringBuffer.append("}");
            stringBuffer.append("return bytes;");
            stringBuffer.append("}");
            CtMethod readStreamOverRiding = CtMethod.make(stringBuffer.toString(), ctClass);
            ctClass.addMethod(readStreamOverRiding);
            StringBuffer constructorStrBuff = new StringBuffer();
            constructorStrBuff.append("public ClassReader(String key,String name,String path,java.io.InputStream inputStream) throws java.io.IOException");
            constructorStrBuff.append("{this(readStream(key,name,path,inputStream, false));}");
//            constructorStrBuff.append("{System.out.println(\"ClassLoader constructor name: \"+name+\" key=\"+key);}");
            CtConstructor ctConstructor1 = CtNewConstructor.make(constructorStrBuff.toString(),ctClass);
            ctClass.addConstructor(ctConstructor1);
            ctClass.toClass(loader, ctClass.getClass().getProtectionDomain());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}

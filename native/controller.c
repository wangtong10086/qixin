#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
JNIEXPORT void JNICALL Java_io_github_wangtong10086_qixin_service_Native_socketLabel(JNIEnv *env,jclass type,jboolean enabled){
 const char *label=enabled?"u:object_r:qixin_socket:s0":"";
 int fd=open("/proc/thread-self/attr/sockcreate",O_WRONLY|O_CLOEXEC);
 if(fd>=0){ssize_t n=write(fd,label,strlen(label));int error=errno;close(fd);errno=error;if(n==(ssize_t)strlen(label))return;}
 char message[256];snprintf(message,sizeof(message),"Socket label: %s",strerror(errno));(*env)->ThrowNew(env,(*env)->FindClass(env,"java/io/IOException"),message);
}

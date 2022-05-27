#import <Foundation/Foundation.h>
#import <MicrosoftCognitiveServicesSpeech/SPXSpeechApi.h>

@interface SimpleAudioRecorder : NSObject

- (instancetype)initWithPushStream:(SPXPushAudioInputStream *)stream;

@property (nonatomic, assign, readonly) BOOL isRunning;

- (bool)record;

- (void)stop;

@end

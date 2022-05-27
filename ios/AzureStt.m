#import "AzureStt.h"
#import "SimpleAudioRecorder.h"
#import <React/RCTLog.h>
#import <UIKit/UIKit.h>
#import <React/RCTUtils.h>
#import <React/RCTEventEmitter.h>
#import <MicrosoftCognitiveServicesSpeech/SPXSpeechApi.h>

@interface AzureStt ()
    
@end

@implementation AzureStt
{
    SimpleAudioRecorder *recorder;
    SPXSpeechRecognizer* speechRecognizer;
    NSTimer *allTimer;
    NSTimer *timer;
}

- (instancetype)init
{
    self = [super init];
    if (self) {
    }
    return self;
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[
        @"onSpeechResults",
        @"onSpeechStart",
        @"onSpeechPartialResults",
        @"onSpeechError",
        @"onSpeechEnd",
        @"onSpeechRecognized",
        @"onSpeechVolumeChanged"
    ];
}

-(void) onStop:(NSTimer *)timer {
    [self teardown];
}

- (void) teardown {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), ^{
        [self stopTimer];
        if (self->recorder) {
            // End recognition request
            NSLog(@"call stop recording");
            [self->recorder stop];
            self->recorder = nil;
        }
        if (self->speechRecognizer) {
            NSLog(@"call stopContinuousRecognition");
            SPXSpeechRecognizer* instance = self->speechRecognizer;
            self->speechRecognizer = nil;
            [instance stopContinuousRecognition];
        }
        NSLog(@"call stopContinuousRecognition");
    });
    
}

- (void) startTimer:(NSTimeInterval)inteval {
    [self stopTimer];
    timer = [NSTimer scheduledTimerWithTimeInterval:inteval
        target:self
        selector:@selector(onStop:)
        userInfo:nil
        repeats:NO];
}

- (void) stopTimer {
    
    if (self->timer) {
        [self->timer invalidate];
        self->timer = nil;
    }
}

RCT_EXPORT_METHOD(isRecognizing:(RCTResponseSenderBlock)callback) {
    if (speechRecognizer != nil) {
        callback(@[@true]);
    } else {
        callback(@[@false]);
    }
}

RCT_EXPORT_METHOD(stopSpeech)
{
    [speechRecognizer stopContinuousRecognition];
}

RCT_EXPORT_METHOD(destroySpeech:(RCTResponseSenderBlock)callback) {
    [self teardown];
    callback(@[@false]);
}

RCT_EXPORT_METHOD(startSpeech:(NSString*)sub)
{
    [self teardown];
    allTimer = [NSTimer scheduledTimerWithTimeInterval:30.0
        target:self
        selector:@selector(onStop:)
        userInfo:nil
        repeats:NO];
    __weak AzureStt *stt = self;
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), ^{
        [stt teardown];
        
//        NSString* pronunciationAssessmentReferenceText = @"Hello world.";
        SPXSpeechConfiguration *speechConfig = [[SPXSpeechConfiguration alloc] initWithSubscription:sub region:@"koreacentral"];
        if (!speechConfig) {
            NSLog(@"Could not load speech config");
            return;
        }

        SPXPushAudioInputStream *stream = [[SPXPushAudioInputStream alloc] init];
        self->recorder = [[SimpleAudioRecorder alloc]initWithPushStream:stream];
        SPXAudioConfiguration *audioConfig = [[SPXAudioConfiguration alloc]initWithStreamInput:stream];

        self->speechRecognizer = [[SPXSpeechRecognizer alloc] initWithSpeechConfiguration:speechConfig audioConfiguration:audioConfig];
                
//        SPXPhraseListGrammar * phraseListGrammar = [[SPXPhraseListGrammar alloc] initWithRecognizer:self->speechRecognizer];
//        [phraseListGrammar addPhrase:@"And worst of all, Angela is considering going home."];
        
        if (!self->speechRecognizer) {
            NSLog(@"Could not create speech recognizer");
            [self sendEventWithName:@"onSpeechError" body:@{@"error": @{@"code": @"recognition_fail"}}];
            return;
        }

        // create pronunciation assessment config, set grading system, granularity and if enable miscue based on your requirement.
    //    SPXPronunciationAssessmentConfiguration *pronunicationConfig =
    //    [[SPXPronunciationAssessmentConfiguration alloc] init:pronunciationAssessmentReferenceText
    //                                            gradingSystem:SPXPronunciationAssessmentGradingSystem_HundredMark
    //                                              granularity:SPXPronunciationAssessmentGranularity_Phoneme
    //                                             enableMiscue:true];

    //    [pronunicationConfig applyToRecognizer:speechRecognizer];
        
        [self->speechRecognizer addRecognizingEventHandler: ^ (SPXSpeechRecognizer *recognizer, SPXSpeechRecognitionEventArgs *eventArgs) {
            NSLog(@"Received intermediate result event. SessionId: %@, recognition result:%@. Status %ld. offset %llu duration %llu resultid:%@", eventArgs.sessionId, eventArgs.result.text, (long)eventArgs.result.reason, eventArgs.result.offset, eventArgs.result.duration, eventArgs.result.resultId);
            
            NSMutableArray* transcriptions = [NSMutableArray new];
            [transcriptions addObject:eventArgs.result.text];
            [stt startTimer:3.0];
            [stt sendEventWithName:@"onSpeechPartialResults" body:@{@"value":transcriptions} ];
        }];

        [self->speechRecognizer addRecognizedEventHandler: ^ (SPXSpeechRecognizer *recognizer, SPXSpeechRecognitionEventArgs *eventArgs) {
            NSLog(@"Received final result event. SessionId: %@, recognition result:%@. Status %ld. offset %llu duration %llu resultid:%@", eventArgs.sessionId, eventArgs.result.text, (long)eventArgs.result.reason, eventArgs.result.offset, eventArgs.result.duration, eventArgs.result.resultId);
            [stt teardown];
            NSMutableArray* transcriptionDics = [NSMutableArray new];
            [transcriptionDics addObject:eventArgs.result.text];
            [stt sendEventWithName:@"onSpeechResults" body:@{@"value":@[eventArgs.result.text]} ];
        }];

        __block bool end = false;
        [self->speechRecognizer addSessionStoppedEventHandler: ^ (SPXRecognizer *recognizer, SPXSessionEventArgs *eventArgs) {
            NSLog(@"Received session stopped event. SessionId: %@", eventArgs.sessionId);
            end = true;
            [stt teardown];
            [stt sendEventWithName:@"onSpeechEnd" body:nil];
        }];
        
        [self->speechRecognizer addCanceledEventHandler:^(SPXSpeechRecognizer * recognizer, SPXSpeechRecognitionCanceledEventArgs *eventArgs) {
            NSLog(@"Received session canceled event.");
            [stt teardown];
        }];
        
        [self->speechRecognizer addSessionStartedEventHandler:^(SPXRecognizer * recognizer, SPXSessionEventArgs *eventArgs) {
            NSLog(@"Received session started event.");
            [stt sendEventWithName:@"onSpeechStart" body:nil];
        }];
        
        bool result = [self->recorder record];
        if (result) {
            [self->speechRecognizer startContinuousRecognition];
        } else {
            [self sendEventWithName:@"onSpeechError" body:@{@"error": @{@"code": @"recognition_fail"}}];
        }
    });
}

RCT_EXPORT_MODULE()
@end

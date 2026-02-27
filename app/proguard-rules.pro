# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK.
# For more details, see
#   https://developer.android.com/build/shrink-code

# Keep StepperLayout and its public methods
-keep public class com.example.stepperfeedback.StepperLayout { *; }
-keep public class com.example.stepperfeedback.StepperLayout$* { *; }

# Keep all feedback types
-keep class com.example.stepperfeedback.feedback.** { *; }

# Keep custom widgets
-keep class com.example.stepperfeedback.widget.** { *; }

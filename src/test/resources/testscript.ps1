echo 'Test message to stdout'
$host.ui.WriteErrorLine('Test message to stderr')

Add-AppveyorTest -Name Test1 -Framework MyTest -FileName testfile.txt -Duration 123 -ErrorMessage 'Test err test msg' -ErrorStackTrace 'testfunc()' -StdOut 'Test stdout' -StdErr 'Test stderr'
Add-AppveyorTest -Name TestNone -Framework MyTest -FileName testfile.txt -Outcome None
Add-AppveyorTest -Name TestRunning -Framework MyTest -FileName testfile.txt -Outcome Running
Add-AppveyorTest -Name TestPassed -Framework MyTest -FileName testfile.txt -Outcome Passed
Add-AppveyorTest -Name TestFailed -Framework MyTest -FileName testfile.txt -Outcome Failed
Add-AppveyorTest -Name TestIgnored -Framework MyTest -FileName testfile.txt -Outcome Ignored
Add-AppveyorTest -Name TestSkipped -Framework MyTest -FileName testfile.txt -Outcome Skipped
Add-AppveyorTest -Name TestInconclusive -Framework MyTest -FileName testfile.txt -Outcome Inconclusive
Add-AppveyorTest -Name TestNotFound -Framework MyTest -FileName testfile.txt -Outcome NotFound
Add-AppveyorTest -Name TestCancelled -Framework MyTest -FileName testfile.txt -Outcome Cancelled
Add-AppveyorTest -Name TestNotRunnable -Framework MyTest -FileName testfile.txt -Outcome NotRunnable

echo $null >> testfile.txt
Push-AppveyorArtifact testfile.txt -FileName testfile2.txt -DeploymentName testfile
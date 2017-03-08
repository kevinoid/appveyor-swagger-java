echo 'Test message to stdout'
$host.ui.WriteErrorLine('Test message to stderr')

Add-AppveyorMessage -Message 'Test info message' -Category Information -Details 'Test info details'
Add-AppveyorMessage -Message 'Test warn message' -Category Warning -Details 'Test warn details'
Add-AppveyorMessage -Message 'Test err message' -Category Error -Details 'Test err details'

Add-AppveyorCompilationMessage -Message 'Test info compile message' -Category Information -Details 'Test info compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt
Add-AppveyorCompilationMessage -Message 'Test warn compile message' -Category Warning -Details 'Test warn compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt
Add-AppveyorCompilationMessage -Message 'Test err compile message' -Category Error -Details 'Test err compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt
echo 'Test message to stdout'
$host.ui.WriteErrorLine('Test message to stderr')

Add-AppveyorMessage -Message 'Test info message' -Category Information -Details 'Test info details'
Add-AppveyorMessage -Message 'Test warn message' -Category Warning -Details 'Test warn details'
Add-AppveyorMessage -Message 'Test err message' -Category Error -Details 'Test err details'

Add-AppveyorCompilationMessage -Message 'Test info compile message' -Category Information -Details 'Test info compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt
Add-AppveyorCompilationMessage -Message 'Test warn compile message' -Category Warning -Details 'Test warn compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt
Add-AppveyorCompilationMessage -Message 'Test err compile message' -Category Error -Details 'Test err compile details' -FileName testfile.txt -Line 123 -Column 4 -ProjectName testproj -ProjectFileName testprojfile.txt

New-Item -Force -ItemType directory bin > $null
Write-Output 'Example content' > bin\artifact.txt

Add-Type -Assembly 'System.IO.Compression.FileSystem'
[System.IO.Compression.ZipFile]::CreateFromDirectory($pwd.Path + "\bin", $pwd.Path + "\binaries.zip")

# Test each artifact type
Foreach ($type in [Enum]::GetNames('Appveyor.BuildAgent.Api.ArtifactType')) {
	Push-AppveyorArtifact binaries.zip -FileName ($type + '.zip') -DeploymentName ($type + '.zip') -Type $type
}
# Test \ in paths
Push-AppveyorArtifact bin\artifact.txt -FileName bin\file.txt -DeploymentName bin\deploy.txt -Type File
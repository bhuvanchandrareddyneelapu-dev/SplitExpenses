$ErrorActionPreference = 'Stop'

$baseUrl = "http://localhost:8080/api"
$rand = Get-Random
$inviterEmail = "inviter$rand@test.com"
$targetEmail = $env:MAIL_USERNAME

Write-Host "Target email is: $targetEmail"

# Register Inviter
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body "{`"fullName`":`"SMTP Tester`", `"email`":`"$inviterEmail`", `"password`":`"password123`"}"
Write-Host "Inviter registered: $inviterEmail"

# Login Inviter
$loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body "{`"email`":`"$inviterEmail`", `"password`":`"password123`"}"
$token = $loginRes.token

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# Create Group
$groupRes = Invoke-RestMethod -Uri "$baseUrl/groups" -Method Post -Headers $headers -Body '{"groupName":"SMTP Test Group", "description":"Testing real email"}'
$groupId = $groupRes.id
Write-Host "Group created with ID: $groupId"

# Invite Target Email
Write-Host "Inviting $targetEmail..."
try {
    $inviteRes = Invoke-RestMethod -Uri "$baseUrl/groups/$groupId/invite" -Method Post -Headers $headers -Body "{`"email`":`"$targetEmail`"}"
    Write-Host "Successfully sent invite email!"
    Write-Host "Response Token: $($inviteRes.invitationToken)"
} catch {
    Write-Host "Error occurred while sending invite:"
    Write-Host $_
    Write-Host $_.Exception.Response
}

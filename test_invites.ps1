$ErrorActionPreference = 'Stop'

$baseUrl = "http://localhost:8080/api"

$rand = Get-Random
$inviterEmail = "inviter$rand@test.com"
$existingEmail = "existing$rand@test.com"
$newEmail = "newuser$rand@test.com"

# 1. Register Inviter
$inviterReg = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body "{`"fullName`":`"Inviter User`", `"email`":`"$inviterEmail`", `"password`":`"password123`"}"
Write-Host "Inviter registered: $($inviterReg.email)"

# 2. Register Existing User
$existingReg = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body "{`"fullName`":`"Existing User`", `"email`":`"$existingEmail`", `"password`":`"password123`"}"
Write-Host "Existing User registered: $($existingReg.email)"

# 3. Login Inviter to get Token
$loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body "{`"email`":`"$inviterEmail`", `"password`":`"password123`"}"
$token = $loginRes.token
Write-Host "Inviter logged in. Token length: $($token.Length)"

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# 4. Create Group
$groupRes = Invoke-RestMethod -Uri "$baseUrl/groups" -Method Post -Headers $headers -Body '{"groupName":"MailDev Test Group", "description":"Testing emails"}'
$groupId = $groupRes.id
Write-Host "Group created with ID: $groupId"

# 5. Invite Existing User
Write-Host "Inviting existing user..."
$inviteExistingRes = Invoke-RestMethod -Uri "$baseUrl/groups/$groupId/invite" -Method Post -Headers $headers -Body "{`"email`":`"$existingEmail`"}"
Write-Host "Invited existing user. Token: $($inviteExistingRes.invitationToken)"

# 6. Invite New User
Write-Host "Inviting new user..."
$inviteNewRes = Invoke-RestMethod -Uri "$baseUrl/groups/$groupId/invite" -Method Post -Headers $headers -Body "{`"email`":`"$newEmail`"}"
Write-Host "Invited new user. Token: $($inviteNewRes.invitationToken)"

Write-Host "API requests completed successfully!"

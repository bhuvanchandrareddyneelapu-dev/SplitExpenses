// SplitWiseMoney - Frontend Application Controller
const App = {
    // Shared state variables
    currentUser: null,
    
    // Page router helper
    checkAuth() {
        const token = API.getToken();
        const currentPath = window.location.pathname;
        const isPublicPage = currentPath.endsWith('index.html') || currentPath.endsWith('/') || currentPath.endsWith('login.html') || currentPath.endsWith('register.html');
        
        if (!token && !isPublicPage) {
            window.location.href = 'login.html';
        }
    },

    // Initialize global header components (user name, unread notification counts, logout handler)
    async initHeader() {
        this.checkAuth();
        const token = API.getToken();
        if (!token) return;

        // Set up logout link
        const logoutLink = document.getElementById('logoutLink');
        if (logoutLink) {
            logoutLink.addEventListener('click', (e) => {
                e.preventDefault();
                API.clearToken();
                window.location.href = 'login.html';
            });
        }

        try {
            // Load user profile for display
            const profile = await API.get('/api/users/me');
            this.currentUser = profile;
            
            const headerUserName = document.getElementById('headerUserName');
            if (headerUserName) headerUserName.textContent = profile.fullName;
            
            const welcomeUserName = document.getElementById('welcomeUserName');
            if (welcomeUserName) welcomeUserName.textContent = profile.fullName;

            // Load unread notifications badge
            this.loadUnreadNotificationCount();
        } catch (err) {
            console.error('Failed to load profile for header', err);
        }
    },

    async loadUnreadNotificationCount() {
        try {
            const count = await API.get('/api/notifications/unread-count');
            const badge = document.getElementById('unreadNotificationBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count;
                    badge.classList.remove('d-none');
                } else {
                    badge.classList.add('d-none');
                }
            }
        } catch (err) {
            console.error('Failed to load notification count', err);
        }
    },

    // ==========================================
    // 1. DASHBOARD PAGE
    // ==========================================
    async initDashboard() {
        await this.initHeader();
        
        try {
            const dashboardData = await API.get('/api/dashboard');
            
            // Set stats
            document.getElementById('statTotalPaid').textContent = `₹${parseFloat(dashboardData.totalPaid).toFixed(2)}`;
            document.getElementById('statTotalOwed').textContent = `₹${parseFloat(dashboardData.totalOwed).toFixed(2)}`;
            document.getElementById('statTotalReceive').textContent = `₹${parseFloat(dashboardData.amountToReceive).toFixed(2)}`;
            document.getElementById('statTotalGroups').textContent = dashboardData.totalGroups;

            // Render activities
            const activityList = document.getElementById('recentActivityList');
            if (dashboardData.recentActivities.length === 0) {
                activityList.innerHTML = `<p class="text-secondary text-center py-4">No recent activity logs.</p>`;
            } else {
                let html = '<div class="timeline-log">';
                dashboardData.recentActivities.forEach(act => {
                    const date = new Date(act.createdAt).toLocaleString();
                    html += `
                        <div class="list-item-glass mb-3 p-3">
                            <div class="d-flex justify-content-between">
                                <span class="fw-medium">${act.action}</span>
                                <span class="text-secondary small">${date}</span>
                            </div>
                        </div>
                    `;
                });
                html += '</div>';
                activityList.innerHTML = html;
            }

            // Load Quick Groups
            const groups = await API.get('/api/groups');
            const quickGroupList = document.getElementById('quickGroupList');
            if (groups.length === 0) {
                quickGroupList.innerHTML = `
                    <div class="text-center py-4">
                        <p class="text-secondary mb-3">You are not in any groups yet.</p>
                        <a href="groups.html" class="btn btn-primary-glow btn-sm">Create / Join Group</a>
                    </div>
                `;
            } else {
                let html = '<div class="list-group">';
                // Show top 4
                groups.slice(0, 4).forEach(group => {
                    html += `
                        <a href="group-details.html?id=${group.id}" class="list-group-item list-group-item-action list-item-glass d-flex justify-content-between align-items-center mb-2">
                            <span class="fw-semibold text-white"><i class="fa-solid fa-folder-open me-2 text-indigo"></i>${group.groupName}</span>
                            <span class="text-secondary small">Created by ${group.createdByName}</span>
                        </a>
                    `;
                });
                html += '</div>';
                quickGroupList.innerHTML = html;
            }
        } catch (err) {
            console.error('Failed to load dashboard data', err);
        }
    },

    // ==========================================
    // 2. GROUPS PAGE
    // ==========================================
    async initGroups() {
        await this.initHeader();
        this.loadGroupsList();

        // Handle Create Group Form Submit
        const createGroupForm = document.getElementById('createGroupForm');
        if (createGroupForm) {
            createGroupForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const groupName = document.getElementById('groupName').value;
                const modalAlertArea = document.getElementById('modalAlertArea');
                
                modalAlertArea.classList.add('d-none');
                
                try {
                    await API.post('/api/groups', { groupName });
                    
                    // Hide Modal
                    const modalEl = document.getElementById('createGroupModal');
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    modal.hide();
                    
                    // Reset form
                    createGroupForm.reset();
                    
                    // Reload groups
                    this.loadGroupsList();
                } catch (err) {
                    modalAlertArea.textContent = err.message || 'Failed to create group.';
                    modalAlertArea.classList.remove('d-none');
                }
            });
        }
    },

    async loadGroupsList() {
        const grid = document.getElementById('groupGrid');
        try {
            const groups = await API.get('/api/groups');
            if (groups.length === 0) {
                grid.innerHTML = `
                    <div class="col-12 text-center py-5 text-secondary">
                        <i class="fa-solid fa-users-slash fs-1 mb-3"></i>
                        <p class="fs-5">No groups found. Create one to start sharing expenses!</p>
                    </div>
                `;
                return;
            }

            let html = '';
            groups.forEach(group => {
                const date = new Date(group.createdAt).toLocaleDateString();
                html += `
                    <div class="col-md-6 col-lg-4">
                        <div class="glass-card p-4 h-100 d-flex flex-column justify-content-between">
                            <div>
                                <h4 class="fw-bold mb-2 text-truncate">${group.groupName}</h4>
                                <p class="text-secondary small mb-3">Created by ${group.createdByName} on ${date}</p>
                            </div>
                            <div class="mt-4">
                                <a href="group-details.html?id=${group.id}" class="btn btn-secondary-outline w-100">
                                    <i class="fa-solid fa-eye me-2"></i>View Details
                                </a>
                            </div>
                        </div>
                    </div>
                `;
            });
            grid.innerHTML = html;
        } catch (err) {
            grid.innerHTML = `<div class="col-12 alert alert-danger border-0 text-white" style="background: var(--accent-rose);">${err.message}</div>`;
        }
    },

    // ==========================================
    // 3. GROUP DETAILS PAGE
    // ==========================================
    groupExpensesPage: 0,
    groupExpensesTotalPages: 1,

    async initGroupDetails() {
        await this.initHeader();
        
        const urlParams = new URLSearchParams(window.location.search);
        const groupId = urlParams.get('id');
        if (!groupId) {
            window.location.href = 'groups.html';
            return;
        }

        // Set up links
        document.getElementById('btnAddExpenseLink').href = `add-expense.html?groupId=${groupId}`;

        // Load Group details
        this.loadGroupDetails(groupId);
        this.loadGroupMembers(groupId);
        this.loadGroupExpenses(groupId);
        this.loadGroupSettlements(groupId);

        // Edit Group Modal setup
        const editGroupForm = document.getElementById('editGroupForm');
        if (editGroupForm) {
            editGroupForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const groupName = document.getElementById('editGroupName').value;
                try {
                    await API.put(`/api/groups/${groupId}`, { groupName });
                    bootstrap.Modal.getInstance(document.getElementById('editGroupModal')).hide();
                    this.loadGroupDetails(groupId);
                } catch (err) {
                    alert('Failed to rename group: ' + err.message);
                }
            });
        }

        // Delete Group Button
        const btnDeleteGroup = document.getElementById('btnDeleteGroup');
        if (btnDeleteGroup) {
            btnDeleteGroup.addEventListener('click', async () => {
                if (confirm('Are you sure you want to delete this group? All expenses and settlements will be permanently deleted.')) {
                    try {
                        await API.delete(`/api/groups/${groupId}`);
                        window.location.href = 'groups.html';
                    } catch (err) {
                        alert('Failed to delete group: ' + err.message);
                    }
                }
            });
        }

        // Add Member Form
        const addMemberForm = document.getElementById('addMemberForm');
        if (addMemberForm) {
            addMemberForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const email = document.getElementById('newMemberEmail').value;
                const alertArea = document.getElementById('groupAlertArea');
                
                alertArea.classList.add('d-none');
                
                try {
                    await API.post(`/api/groups/${groupId}/members`, { email });
                    document.getElementById('newMemberEmail').value = '';
                    
                    alertArea.textContent = 'Member added successfully!';
                    alertArea.className = 'alert border-0 text-white d-block';
                    alertArea.style.backgroundColor = 'var(--accent-emerald)';
                    
                    this.loadGroupMembers(groupId);
                    this.loadGroupSettlements(groupId);
                    
                    setTimeout(() => alertArea.classList.add('d-none'), 3000);
                } catch (err) {
                    alertArea.textContent = err.message || 'Failed to add member.';
                    alertArea.className = 'alert border-0 text-white d-block';
                    alertArea.style.backgroundColor = 'var(--accent-rose)';
                }
            });
        }

        // Pagination buttons
        document.getElementById('btnPrevPage').addEventListener('click', () => {
            if (this.groupExpensesPage > 0) {
                this.groupExpensesPage--;
                this.loadGroupExpenses(groupId);
            }
        });

        document.getElementById('btnNextPage').addEventListener('click', () => {
            if (this.groupExpensesPage < this.groupExpensesTotalPages - 1) {
                this.groupExpensesPage++;
                this.loadGroupExpenses(groupId);
            }
        });
    },

    async loadGroupDetails(groupId) {
        try {
            const group = await API.get(`/api/groups/${groupId}`);
            document.getElementById('groupDetailName').textContent = group.groupName;
            document.getElementById('editGroupName').value = group.groupName;
        } catch (err) {
            console.error('Failed to load group details', err);
        }
    },

    async loadGroupMembers(groupId) {
        const memberList = document.getElementById('memberList');
        try {
            const members = await API.get(`/api/groups/${groupId}/members`);
            let html = '<div class="list-group">';
            members.forEach(member => {
                const isMe = this.currentUser && this.currentUser.id === member.id;
                html += `
                    <div class="list-group-item list-item-glass d-flex justify-content-between align-items-center mb-2">
                        <div>
                            <span class="fw-semibold text-white">${member.fullName} ${isMe ? '<span class="text-indigo small">(You)</span>' : ''}</span>
                            <br><span class="text-secondary small">${member.email}</span>
                        </div>
                        ${!isMe ? `
                            <button class="btn btn-link text-danger p-0" onclick="App.removeGroupMember(${groupId}, ${member.id})">
                                <i class="fa-solid fa-user-minus"></i>
                            </button>
                        ` : ''}
                    </div>
                `;
            });
            html += '</div>';
            memberList.innerHTML = html;
        } catch (err) {
            memberList.innerHTML = `<p class="text-danger">${err.message}</p>`;
        }
    },

    async removeGroupMember(groupId, userId) {
        if (confirm('Are you sure you want to remove this member?')) {
            try {
                await API.delete(`/api/groups/${groupId}/members/${userId}`);
                this.loadGroupMembers(groupId);
                this.loadGroupSettlements(groupId);
            } catch (err) {
                alert('Failed to remove member: ' + err.message);
            }
        }
    },

    async loadGroupExpenses(groupId) {
        const expList = document.getElementById('expenseList');
        try {
            const pageData = await API.get(`/api/expenses/group/${groupId}?page=${this.groupExpensesPage}&size=5&sort=expenseDate,desc`);
            
            this.groupExpensesTotalPages = pageData.totalPages;
            
            // Update pagination UI
            document.getElementById('expensePaginationText').textContent = `Page ${this.groupExpensesPage + 1} of ${pageData.totalPages || 1}`;
            document.getElementById('btnPrevPage').disabled = this.groupExpensesPage === 0;
            document.getElementById('btnNextPage').disabled = this.groupExpensesPage >= (pageData.totalPages - 1) || pageData.totalPages === 0;

            if (pageData.content.length === 0) {
                expList.innerHTML = `<p class="text-secondary text-center py-4">No expenses recorded in this group yet.</p>`;
                return;
            }

            let html = '';
            pageData.content.forEach(exp => {
                const date = new Date(exp.expenseDate).toLocaleDateString();
                const badgeClass = `badge-${exp.category.toLowerCase()}`;
                
                // Construct participants breakdown
                const partsHtml = exp.participants.map(p => `${p.fullName} (₹${parseFloat(p.shareAmount).toFixed(2)})`).join(', ');

                html += `
                    <div class="list-item-glass p-3 mb-3">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <div>
                                <h5 class="fw-bold mb-1 text-white">${exp.description}</h5>
                                <span class="badge badge-category ${badgeClass} mb-2">${exp.category}</span>
                                <p class="text-secondary mb-0 small">Paid by <strong>${exp.paidByName}</strong> on ${date}</p>
                            </div>
                            <div class="text-end">
                                <h4 class="fw-bold text-white mb-2">₹${parseFloat(exp.amount).toFixed(2)}</h4>
                                <div class="d-flex gap-2 justify-content-end">
                                    <a href="add-expense.html?groupId=${groupId}&expenseId=${exp.id}" class="btn btn-link text-indigo p-0 me-2"><i class="fa-solid fa-edit"></i></a>
                                    <button class="btn btn-link text-danger p-0" onclick="App.deleteExpense(${groupId}, ${exp.id})"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </div>
                        </div>
                        <div class="border-top border-secondary pt-2 mt-2 small text-secondary">
                            <strong>Split between:</strong> ${partsHtml}
                        </div>
                    </div>
                `;
            });
            expList.innerHTML = html;
        } catch (err) {
            expList.innerHTML = `<p class="text-danger">${err.message}</p>`;
        }
    },

    async deleteExpense(groupId, expenseId) {
        if (confirm('Are you sure you want to delete this expense?')) {
            try {
                await API.delete(`/api/expenses/${expenseId}`);
                this.loadGroupExpenses(groupId);
                this.loadGroupSettlements(groupId);
            } catch (err) {
                alert('Failed to delete expense: ' + err.message);
            }
        }
    },

    async loadGroupSettlements(groupId) {
        const consoleEl = document.getElementById('simplifiedSettlementConsole');
        const historyEl = document.getElementById('settlementHistoryList');
        
        try {
            // Load Simplified Settlements
            const simplified = await API.get(`/api/settlements/group/${groupId}/owed`);
            if (simplified.length === 0) {
                consoleEl.innerHTML = `
                    <div class="text-center py-4 text-secondary">
                        <i class="fa-solid fa-handshake-angle fs-2 mb-2 text-success"></i>
                        <p class="mb-0 text-success">Everyone is settled up!</p>
                    </div>
                `;
            } else {
                let html = '<div class="list-group">';
                simplified.forEach((s, idx) => {
                    const isSenderMe = this.currentUser && this.currentUser.id === s.fromUserId;
                    const isReceiverMe = this.currentUser && this.currentUser.id === s.toUserId;
                    
                    html += `
                        <div class="list-group-item list-item-glass d-flex justify-content-between align-items-center mb-2">
                            <div>
                                <span class="fw-semibold text-white">${s.fromUserName}</span>
                                <span class="text-secondary small"> owes </span>
                                <span class="fw-semibold text-white">${s.toUserName}</span>
                                <br><span class="text-secondary small">Amount: </span><strong class="text-success">₹${parseFloat(s.amount).toFixed(2)}</strong>
                            </div>
                            <button class="btn btn-primary-glow btn-sm" onclick="App.recordSettlement(${groupId}, ${s.fromUserId}, ${s.toUserId}, ${s.amount})">
                                <i class="fa-solid fa-check me-1"></i>Settle
                            </button>
                        </div>
                    `;
                });
                html += '</div>';
                consoleEl.innerHTML = html;
            }

            // Load Settlement Transaction Logs
            const history = await API.get(`/api/settlements/group/${groupId}`);
            if (history.length === 0) {
                historyEl.innerHTML = `<p class="text-secondary text-center py-3">No recorded settlements.</p>`;
            } else {
                let html = '<div class="list-group">';
                history.forEach(h => {
                    const date = new Date(h.createdAt).toLocaleDateString();
                    html += `
                        <div class="list-group-item list-item-glass d-flex justify-content-between align-items-center mb-2">
                            <div>
                                <span class="fw-semibold text-white">${h.fromUserName}</span>
                                <span class="text-secondary small"> settled to </span>
                                <span class="fw-semibold text-white">${h.toUserName}</span>
                                <br><span class="text-secondary small">On ${date}</span>
                            </div>
                            <h5 class="fw-bold text-success mb-0">₹${parseFloat(h.amount).toFixed(2)}</h5>
                        </div>
                    `;
                });
                html += '</div>';
                historyEl.innerHTML = html;
            }
        } catch (err) {
            consoleEl.innerHTML = `<p class="text-danger">${err.message}</p>`;
        }
    },

    async recordSettlement(groupId, fromUserId, toUserId, amount) {
        if (confirm(`Confirm settlement payment of ₹${parseFloat(amount).toFixed(2)}?`)) {
            try {
                await API.post(`/api/settlements/group/${groupId}`, {
                    fromUserId,
                    toUserId,
                    amount,
                    status: 'SETTLED'
                });
                
                // Refresh list
                this.loadGroupSettlements(groupId);
                
                // Trigger global badge update
                this.loadUnreadNotificationCount();
            } catch (err) {
                alert('Failed to record settlement: ' + err.message);
            }
        }
    },

    // ==========================================
    // 4. ADD / EDIT EXPENSE PAGE
    // ==========================================
    groupMembersCache: [],
    
    async initAddExpense() {
        await this.initHeader();

        const urlParams = new URLSearchParams(window.location.search);
        const groupId = urlParams.get('groupId');
        const expenseId = urlParams.get('expenseId');

        if (!groupId) {
            window.location.href = 'groups.html';
            return;
        }

        document.getElementById('btnBackToGroup').href = `group-details.html?id=${groupId}`;
        document.getElementById('btnCancelExpense').href = `group-details.html?id=${groupId}`;
        
        // Setup Date picker to today by default
        document.getElementById('expenseDate').valueAsDate = new Date();

        try {
            // Load members to build select list and split layout
            const members = await API.get(`/api/groups/${groupId}/members`);
            this.groupMembersCache = members;

            // Fill PaidBy dropdown
            const paidBySelect = document.getElementById('paidBy');
            paidBySelect.innerHTML = '';
            members.forEach(member => {
                const opt = document.createElement('option');
                opt.value = member.id;
                opt.textContent = member.fullName;
                if (this.currentUser && member.id === this.currentUser.id) {
                    opt.selected = true;
                }
                paidBySelect.appendChild(opt);
            });

            // Set up split type radio change events
            document.querySelectorAll('input[name="splitMethod"]').forEach(radio => {
                radio.addEventListener('change', () => this.renderSplitUI());
            });

            // Set up amount input event to trigger dynamically updated calculations
            document.getElementById('amount').addEventListener('input', () => this.recalculateShares());

            // Build base table rows
            this.renderSplitUI();

            // Load Edit mode if expenseId is present
            if (expenseId) {
                document.getElementById('expenseFormTitle').textContent = 'Edit Expense';
                document.getElementById('btnSubmitExpense').textContent = 'Update Expense';
                
                const exp = await API.get(`/api/expenses/${expenseId}`);
                document.getElementById('amount').value = exp.amount;
                document.getElementById('description').value = exp.description;
                document.getElementById('category').value = exp.category;
                document.getElementById('expenseDate').value = exp.expenseDate;
                document.getElementById('paidBy').value = exp.paidById;

                // Load existing shares to map split mode
                // Detect split method based on values
                // First check if equal split
                const participantCount = exp.participants.length;
                let isEquallySplit = true;
                const expectedEqually = (exp.amount / participantCount).toFixed(2);
                
                exp.participants.forEach(p => {
                    if (Math.abs(p.shareAmount - expectedEqually) > 0.05) {
                        isEquallySplit = false;
                    }
                });

                if (isEquallySplit) {
                    document.getElementById('splitEqual').checked = true;
                } else {
                    document.getElementById('splitCustom').checked = true;
                }
                
                this.renderSplitUI();

                // Check correct users in checkboxes, fill inputs
                exp.participants.forEach(p => {
                    const chk = document.getElementById(`partChk_${p.userId}`);
                    if (chk) chk.checked = true;

                    const input = document.getElementById(`partInput_${p.userId}`);
                    if (input) input.value = p.shareAmount;
                });

                this.recalculateShares();
            }

        } catch (err) {
            console.error('Failed to init expense form', err);
        }

        // Form Submit
        const expenseForm = document.getElementById('expenseForm');
        expenseForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const alertArea = document.getElementById('expenseAlertArea');
            alertArea.classList.add('d-none');

            const amount = parseFloat(document.getElementById('amount').value);
            const description = document.getElementById('description').value;
            const category = document.getElementById('category').value;
            const expenseDate = document.getElementById('expenseDate').value;
            const paidById = parseInt(document.getElementById('paidBy').value);

            // Compute participant shares map
            const participantShares = {};
            let selectedCount = 0;
            
            this.groupMembersCache.forEach(m => {
                const chk = document.getElementById(`partChk_${m.id}`);
                if (chk && chk.checked) {
                    selectedCount++;
                    const input = document.getElementById(`partInput_${m.id}`);
                    participantShares[m.id] = parseFloat(input.value || 0);
                }
            });

            if (selectedCount === 0) {
                alertArea.textContent = 'Please select at least one participant.';
                alertArea.classList.remove('d-none');
                return;
            }

            // Client side sum validation
            let sum = 0;
            for (let val of Object.values(participantShares)) {
                sum += val;
            }

            if (Math.abs(sum - amount) > 0.05) {
                alertArea.textContent = `The sum of shares (₹${sum.toFixed(2)}) must equal the total amount (₹${amount.toFixed(2)}).`;
                alertArea.classList.remove('d-none');
                return;
            }

            try {
                if (expenseId) {
                    await API.put(`/api/expenses/${expenseId}`, {
                        amount,
                        description,
                        category,
                        expenseDate,
                        paidById,
                        participantShares
                    });
                } else {
                    await API.post(`/api/expenses/group/${groupId}`, {
                        amount,
                        description,
                        category,
                        expenseDate,
                        paidById,
                        participantShares
                    });
                }
                window.location.href = `group-details.html?id=${groupId}`;
            } catch (err) {
                alertArea.textContent = err.message || 'Failed to submit expense.';
                alertArea.classList.remove('d-none');
            }
        });
    },

    renderSplitUI() {
        const method = document.querySelector('input[name="splitMethod"]:checked').value;
        const tbody = document.getElementById('participantsSplitTableBody');
        const sumDiv = document.getElementById('splitSummaryText');
        
        let headerText = 'Share Amount';
        if (method === 'PERCENT') {
            headerText = 'Share (%)';
            sumDiv.classList.remove('d-none');
        } else if (method === 'CUSTOM') {
            headerText = 'Share (₹)';
            sumDiv.classList.remove('d-none');
        } else {
            sumDiv.classList.add('d-none');
        }
        document.getElementById('shareColumnHeader').textContent = headerText;

        tbody.innerHTML = '';
        this.groupMembersCache.forEach(member => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>
                    <input class="form-check-input part-checkbox" type="checkbox" id="partChk_${member.id}" value="${member.id}" checked>
                </td>
                <td>
                    <span class="fw-semibold text-white">${member.fullName}</span><br>
                    <span class="text-secondary small">${member.email}</span>
                </td>
                <td>
                    <div class="input-group input-group-sm">
                        ${method !== 'EQUAL' ? '' : '<span class="input-group-text bg-transparent text-secondary border-0">₹</span>'}
                        <input type="number" step="0.01" class="form-control form-glass part-share-input" 
                               id="partInput_${member.id}" 
                               ${method === 'EQUAL' ? 'disabled' : ''} 
                               placeholder="0.00">
                    </div>
                </td>
            `;
            tbody.appendChild(tr);

            // Bind events for inputs and check state shifts
            tr.querySelector('.part-checkbox').addEventListener('change', () => this.recalculateShares());
            if (method !== 'EQUAL') {
                tr.querySelector('.part-share-input').addEventListener('input', () => this.recalculateShares());
            }
        });

        this.recalculateShares();
    },

    recalculateShares() {
        const method = document.querySelector('input[name="splitMethod"]:checked').value;
        const amount = parseFloat(document.getElementById('amount').value || 0);
        const sumDiv = document.getElementById('splitSummaryText');

        // Fetch selected members
        const selected = [];
        this.groupMembersCache.forEach(m => {
            const chk = document.getElementById(`partChk_${m.id}`);
            if (chk && chk.checked) {
                selected.push(m.id);
            } else {
                // Clear input if unselected
                const input = document.getElementById(`partInput_${m.id}`);
                if (input) input.value = '';
            }
        });

        if (method === 'EQUAL') {
            if (selected.length > 0) {
                const equalShare = (amount / selected.length);
                selected.forEach((id, idx) => {
                    const input = document.getElementById(`partInput_${id}`);
                    // Adjust rounding difference on final user
                    if (idx === selected.length - 1) {
                        const sumSoFar = equalShare.toFixed(2) * (selected.length - 1);
                        input.value = (amount - sumSoFar).toFixed(2);
                    } else {
                        input.value = equalShare.toFixed(2);
                    }
                });
            }
        } else if (method === 'PERCENT') {
            let sumPercent = 0;
            selected.forEach(id => {
                const input = document.getElementById(`partInput_${id}`);
                sumPercent += parseFloat(input.value || 0);
            });
            sumDiv.textContent = `Sum of percentages: ${sumPercent.toFixed(2)}% / 100%`;
            if (Math.abs(sumPercent - 100) < 0.05) {
                sumDiv.className = 'text-end text-success small mt-2';
            } else {
                sumDiv.className = 'text-end text-danger small mt-2';
            }
        } else if (method === 'CUSTOM') {
            let sumCustom = 0;
            selected.forEach(id => {
                const input = document.getElementById(`partInput_${id}`);
                sumCustom += parseFloat(input.value || 0);
            });
            sumDiv.textContent = `Sum of shares: ₹${sumCustom.toFixed(2)} / ₹${amount.toFixed(2)}`;
            if (Math.abs(sumCustom - amount) < 0.05) {
                sumDiv.className = 'text-end text-success small mt-2';
            } else {
                sumDiv.className = 'text-end text-danger small mt-2';
            }
        }
    },

    // ==========================================
    // 5. HISTORY PAGE
    // ==========================================
    historyPage: 0,
    historyTotalPages: 1,

    async initHistory() {
        await this.initHeader();
        this.loadHistoryLogs();

        document.getElementById('btnPrevHistoryPage').addEventListener('click', () => {
            if (this.historyPage > 0) {
                this.historyPage--;
                this.loadHistoryLogs();
            }
        });

        document.getElementById('btnNextHistoryPage').addEventListener('click', () => {
            if (this.historyPage < this.historyTotalPages - 1) {
                this.historyPage++;
                this.loadHistoryLogs();
            }
        });
    },

    async loadHistoryLogs() {
        const container = document.getElementById('historyLogsContainer');
        try {
            const pageData = await API.get(`/api/history?page=${this.historyPage}&size=10&sort=createdAt,desc`);
            
            this.historyTotalPages = pageData.totalPages;
            
            document.getElementById('historyPaginationText').textContent = `Page ${this.historyPage + 1} of ${pageData.totalPages || 1}`;
            document.getElementById('btnPrevHistoryPage').disabled = this.historyPage === 0;
            document.getElementById('btnNextHistoryPage').disabled = this.historyPage >= (pageData.totalPages - 1) || pageData.totalPages === 0;

            if (pageData.content.length === 0) {
                container.innerHTML = `<p class="text-secondary text-center py-4">No logged history yet.</p>`;
                return;
            }

            let html = '';
            pageData.content.forEach(log => {
                const date = new Date(log.createdAt).toLocaleString();
                // Pick a dot color based on action keywords
                let dotColorClass = '';
                if (log.action.toLowerCase().includes('settled') || log.action.toLowerCase().includes('payment')) {
                    dotColorClass = 'green';
                } else if (log.action.toLowerCase().includes('delete') || log.action.toLowerCase().includes('remove')) {
                    dotColorClass = 'red';
                }

                html += `
                    <div class="timeline-log mb-4 position-relative ps-4" style="border-left: 2px solid var(--border-color);">
                        <div class="timeline-dot ${dotColorClass}"></div>
                        <div class="glass-card p-3">
                            <div class="d-flex justify-content-between">
                                <span class="fw-semibold text-white">${log.action}</span>
                                <span class="text-secondary small">${date}</span>
                            </div>
                            <span class="text-secondary small">Triggered by ${log.userName}</span>
                        </div>
                    </div>
                `;
            });
            container.innerHTML = html;
        } catch (err) {
            container.innerHTML = `<p class="text-danger">${err.message}</p>`;
        }
    },

    // ==========================================
    // 6. NOTIFICATIONS PAGE
    // ==========================================
    notificationsPage: 0,
    notificationsTotalPages: 1,

    async initNotifications() {
        await this.initHeader();
        this.loadNotifications();

        document.getElementById('btnMarkAllRead').addEventListener('click', async () => {
            try {
                await API.post('/api/notifications/mark-read', {});
                this.loadNotifications();
                this.loadUnreadNotificationCount();
            } catch (err) {
                alert('Failed to mark all as read: ' + err.message);
            }
        });

        document.getElementById('btnPrevNotificationsPage').addEventListener('click', () => {
            if (this.notificationsPage > 0) {
                this.notificationsPage--;
                this.loadNotifications();
            }
        });

        document.getElementById('btnNextNotificationsPage').addEventListener('click', () => {
            if (this.notificationsPage < this.notificationsTotalPages - 1) {
                this.notificationsPage++;
                this.loadNotifications();
            }
        });
    },

    async loadNotifications() {
        const container = document.getElementById('notificationsContainer');
        try {
            const pageData = await API.get(`/api/notifications?page=${this.notificationsPage}&size=10&sort=createdAt,desc`);
            
            this.notificationsTotalPages = pageData.totalPages;
            
            document.getElementById('notificationsPaginationText').textContent = `Page ${this.notificationsPage + 1} of ${pageData.totalPages || 1}`;
            document.getElementById('btnPrevNotificationsPage').disabled = this.notificationsPage === 0;
            document.getElementById('btnNextNotificationsPage').disabled = this.notificationsPage >= (pageData.totalPages - 1) || pageData.totalPages === 0;

            if (pageData.content.length === 0) {
                container.innerHTML = `<p class="text-secondary text-center py-4">No notifications.</p>`;
                return;
            }

            let html = '';
            pageData.content.forEach(n => {
                const date = new Date(n.createdAt).toLocaleString();
                
                // Color glow based on notification status
                const indicatorClass = n.isRead ? 'bg-secondary' : 'bg-primary-glow';
                const opacity = n.isRead ? 'opacity: 0.65;' : '';

                html += `
                    <div class="list-item-glass p-3 mb-3 d-flex align-items-center gap-3" style="${opacity}">
                        <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 12px; height: 12px; min-width: 12px; ${n.isRead ? 'background: #9aa0b9;' : 'background: linear-gradient(135deg, var(--accent-indigo) 0%, var(--accent-violet) 100%);'}"></div>
                        <div class="w-100">
                            <div class="d-flex justify-content-between">
                                <span class="fw-medium text-white">${n.message}</span>
                                <span class="text-secondary small">${date}</span>
                            </div>
                        </div>
                    </div>
                `;
            });
            container.innerHTML = html;
        } catch (err) {
            container.innerHTML = `<p class="text-danger">${err.message}</p>`;
        }
    },

    // ==========================================
    // 7. USER PROFILE PAGE
    // ==========================================
    async initProfile() {
        await this.initHeader();
        
        try {
            const profile = await API.get('/api/users/me');
            document.getElementById('profileDisplayName').textContent = profile.fullName;
            document.getElementById('profileDisplayEmail').textContent = profile.email;
            document.getElementById('profileFullName').value = profile.fullName;
            document.getElementById('profileEmailReadOnly').value = profile.email;
            document.getElementById('profileCreatedAt').value = new Date(profile.createdAt).toLocaleString();
        } catch (err) {
            console.error('Failed to load profile details', err);
        }

        // Form Submit
        const profileForm = document.getElementById('profileForm');
        profileForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fullName = document.getElementById('profileFullName').value;
            const alertArea = document.getElementById('profileAlertArea');
            
            alertArea.classList.add('d-none');

            try {
                const updated = await API.put('/api/users/me', { fullName });
                
                document.getElementById('profileDisplayName').textContent = updated.fullName;
                const headerUserName = document.getElementById('headerUserName');
                if (headerUserName) headerUserName.textContent = updated.fullName;

                alertArea.textContent = 'Profile updated successfully!';
                alertArea.className = 'alert border-0 text-white d-block';
                alertArea.style.backgroundColor = 'var(--accent-emerald)';
                
                setTimeout(() => alertArea.classList.add('d-none'), 3000);
            } catch (err) {
                alertArea.textContent = err.message || 'Failed to update profile.';
                alertArea.className = 'alert border-0 text-white d-block';
                alertArea.style.backgroundColor = 'var(--accent-rose)';
            }
        });
    },

    // ==========================================
    // 8. SETTINGS PAGE
    // ==========================================
    async initSettings() {
        await this.initHeader();

        // Form Submit
        const changePasswordForm = document.getElementById('changePasswordForm');
        changePasswordForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const oldPassword = document.getElementById('oldPassword').value;
            const newPassword = document.getElementById('newPassword').value;
            const alertArea = document.getElementById('settingsAlertArea');
            
            alertArea.classList.add('d-none');

            try {
                await API.post('/api/users/me/change-password', { oldPassword, newPassword });
                changePasswordForm.reset();

                alertArea.textContent = 'Password updated successfully!';
                alertArea.className = 'alert border-0 text-white d-block';
                alertArea.style.backgroundColor = 'var(--accent-emerald)';
                
                setTimeout(() => alertArea.classList.add('d-none'), 3000);
            } catch (err) {
                alertArea.textContent = err.message || 'Failed to update password. Please check your current password.';
                alertArea.className = 'alert border-0 text-white d-block';
                alertArea.style.backgroundColor = 'var(--accent-rose)';
            }
        });
    }
};

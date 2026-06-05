<template>
  <div class="teams-container">
    <div class="page-header">
      <h2>团队管理 (Teams)</h2>
      <p class="subtitle">创建和管理您的创意团队，共享项目空间与资产。</p>
    </div>

    <n-row :gutter="24">
      <!-- 左侧：团队列表 -->
      <n-col :span="10">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="card-header-row">
              <span>我的团队 ({{ teamsCountDisplay }})</span>
              <n-button type="primary" size="small" @click="showCreateModal = true">
                <template #icon><Plus /></template>新建团队
              </n-button>
            </div>
          </template>

          <div v-if="teamsLoading && !teamsReady" class="loading-box">
            <n-spin size="small" />
          </div>
          <div class="team-list" v-else-if="teamsReady && teamStore.teams.length > 0">
            <div
              v-for="team in pagedTeams"
              :key="team.id"
              class="team-card"
              :class="{ 'active-team': selectedTeam?.id === team.id }"
              @click="handleSelectTeam(team)"
            >
              <div class="team-avatar-box">
                <n-avatar round :size="44" color="#10b981">
                  {{ team.name.charAt(0).toUpperCase() }}
                </n-avatar>
              </div>
              <div class="team-info">
                <span class="team-name">{{ team.name }}</span>
                <span class="team-meta">{{ formatMemberCount(team.memberCount) }}</span>
              </div>
              <n-dropdown v-if="team.ownerId === userStore.userInfo?.id" trigger="click" :options="teamActions" @select="(key: string) => handleTeamAction(key, team)">
                <n-button size="tiny" quaternary @click.stop><template #icon><MoreVertical /></template></n-button>
              </n-dropdown>
            </div>
          </div>
          <n-empty v-else-if="teamsReady" description="暂无团队，点击右上角创建" />
          <n-empty v-else description="团队数据待确认，请稍后重试。" />
          <div class="pager" v-if="teamsReady && teamStore.teams.length > pageSize">
            <n-pagination v-model:page="page" :page-size="pageSize" :item-count="teamStore.teams.length" simple />
          </div>
        </n-card>
      </n-col>

      <!-- 右侧：成员列表 -->
      <n-col :span="14">
        <n-card class="glass-card" :bordered="false" v-if="selectedTeam">
          <template #header>
            <div class="card-header-row">
              <div class="header-col">
                <span>{{ selectedTeam.name }} · 成员 ({{ membersCountDisplay }})</span>
                <span class="team-desc" v-if="(selectedTeam as any).description">{{ (selectedTeam as any).description }}</span>
              </div>
              <n-space>
                <n-input v-model:value="memberSearch" placeholder="搜索成员..." style="width:160px;" size="small" clearable>
                  <template #prefix><Search class="s-icon" /></template>
                </n-input>
                <n-button type="primary" size="small" secondary @click="showInviteModal = true">
                  <template #icon><UserPlus /></template>邀请成员
                </n-button>
              </n-space>
            </div>
          </template>

          <div v-if="membersLoading && !membersReady" class="loading-box">
            <n-spin size="small" />
          </div>
          <div v-else class="member-list">
            <div v-for="member in filteredMembers" :key="member.id" class="member-row">
              <n-avatar round :size="36" :src="member.avatar" color="#3b82f6">
                {{ memberInitial(member) }}
              </n-avatar>
              <div class="member-info">
                <span class="member-name">{{ memberDisplayName(member) }}</span>
                <span class="member-username">@{{ member.username }}</span>
              </div>
              <n-tag :type="roleType(member.role)" size="small">{{ roleLabel(member.role) }}</n-tag>
              <n-button v-if="canManage(member)" size="tiny" type="error" tertiary @click="handleRemoveMember(member)">
                移除
              </n-button>
            </div>
            <n-empty v-if="membersReady && filteredMembers.length===0" description="无匹配成员" style="padding:20px 0;" />
            <n-empty v-else-if="!membersReady" description="成员数据待确认，请稍后重试。" style="padding:20px 0;" />
          </div>
        </n-card>
        <n-card class="glass-card" :bordered="false" v-else>
          <n-empty description="请从左侧选择一个团队查看成员" />
        </n-card>
      </n-col>
    </n-row>

    <!-- 创建团队 -->
    <n-modal v-model:show="showCreateModal" preset="card" title="创建新团队" style="width:460px;" closable>
      <n-form :model="createForm" label-placement="top">
        <n-form-item label="团队名称" required>
          <n-input v-model:value="createForm.name" placeholder="例如：创意设计组" :maxlength="40" show-count />
        </n-form-item>
        <n-form-item label="团队描述">
          <n-input v-model:value="createForm.description" type="textarea" :rows="3" placeholder="团队介绍..." :maxlength="200" show-count />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="showCreateModal = false">取消</n-button>
        <n-button type="primary" @click="handleCreateTeam" :loading="creating">创建</n-button>
      </template>
    </n-modal>

    <!-- 编辑团队 -->
    <n-modal v-model:show="showEditModal" preset="card" title="编辑团队" style="width:460px;" closable>
      <n-form :model="editForm" label-placement="top">
        <n-form-item label="团队名称">
          <n-input v-model:value="editForm.name" :maxlength="40" show-count />
        </n-form-item>
        <n-form-item label="团队描述">
          <n-input v-model:value="editForm.description" type="textarea" :rows="3" :maxlength="200" show-count />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="showEditModal = false">取消</n-button>
        <n-button type="primary" @click="handleEditTeam">保存</n-button>
      </template>
    </n-modal>

    <!-- 邀请成员 -->
    <n-modal v-model:show="showInviteModal" preset="card" title="邀请成员" style="width:460px;" closable>
      <n-form :model="inviteForm" label-placement="top">
        <n-form-item label="对方用户名" required>
          <n-input v-model:value="inviteForm.username" placeholder="输入对方的系统用户名" />
        </n-form-item>
        <n-form-item label="角色">
          <n-select v-model:value="inviteForm.role" :options="[{label:'成员',value:'member'},{label:'管理员',value:'admin'}]" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="showInviteModal = false">取消</n-button>
        <n-button type="primary" @click="handleInviteMember" :loading="inviting">邀请</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { useUserStore } from '@/store/user'
import { useTeamStore } from '@/store/team'
import { teamApi, type Team } from '@/api/teams'
import { Plus, Trash2, UserPlus, Search, Edit3, MoreVertical } from 'lucide-vue-next'

const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()
const teamStore = useTeamStore()

const selectedTeam = ref<Team | null>(null)
const showCreateModal = ref(false)
const showEditModal = ref(false)
const showInviteModal = ref(false)
const creating = ref(false)
const inviting = ref(false)
const memberSearch = ref('')
const teamsLoading = ref(true)
const teamsReady = ref(false)
const membersLoading = ref(false)
const membersReady = ref(false)

const createForm = ref({ name: '', description: '' })
const editForm = ref({ id: 0, name: '', description: '' })
const inviteForm = ref({ username: '', role: 'member' })
const teamsCountDisplay = computed(() => teamsReady.value ? teamStore.teams.length : '-')
const membersCountDisplay = computed(() => membersReady.value ? teamStore.currentMembers.length : '-')

// 前端分页(teamStore 全量不动)
const page = ref(1)
const pageSize = 10
const pagedTeams = computed(() => {
  const start = (page.value - 1) * pageSize
  return teamStore.teams.slice(start, start + pageSize)
})

const teamActions = [
  { label: '编辑团队', key: 'edit', icon: () => Edit3 },
  { label: '解散团队', key: 'delete', icon: () => Trash2 }
]

const filteredMembers = computed(() => {
  if (!memberSearch.value) return teamStore.currentMembers
  const q = memberSearch.value.toLowerCase()
  return teamStore.currentMembers.filter(m =>
    memberDisplayName(m).toLowerCase().includes(q) || m.username?.toLowerCase().includes(q)
  )
})

function formatMemberCount(memberCount?: number) {
  return typeof memberCount === 'number' ? `${memberCount} 成员` : '成员数待确认'
}

function memberDisplayName(member: { nickname?: string; username?: string }) {
  return member.nickname?.trim() || member.username?.trim() || '未知成员'
}

function memberInitial(member: { nickname?: string; username?: string }) {
  return memberDisplayName(member).charAt(0) || '?'
}

function roleType(role: string) {
  return role === 'owner' ? 'warning' : role === 'admin' ? 'info' : 'default'
}
function roleLabel(role: string) {
  if (role === 'owner') return '所有者'
  if (role === 'admin') return '管理员'
  if (role === 'member') return '成员'
  return role || '未知角色'
}
function canManage(member: any) {
  return selectedTeam.value?.ownerId === userStore.userInfo?.id && member.role !== 'owner'
}

async function loadTeams() {
  teamsLoading.value = true
  try {
    await teamStore.refresh()
    teamsReady.value = true
  } catch (err: any) {
    teamsReady.value = false
    message.error(err.message || '加载团队失败')
  } finally {
    teamsLoading.value = false
  }
}

async function loadMembers(teamId: number) {
  membersLoading.value = true
  membersReady.value = false
  try {
    await teamStore.refreshMembers(teamId)
    membersReady.value = true
  } catch (err: any) {
    membersReady.value = false
    message.error(err.message || '加载团队成员失败')
  } finally {
    membersLoading.value = false
  }
}

onMounted(async () => { await loadTeams() })

const handleSelectTeam = async (team: Team) => {
  selectedTeam.value = team
  await loadMembers(team.id)
}

const handleCreateTeam = async () => {
  if (!createForm.value.name) { message.error('请输入团队名称'); return }
  creating.value = true
  try {
    await teamStore.createTeam(createForm.value.name, createForm.value.description)
    teamsReady.value = true
    createForm.value = { name: '', description: '' }
    showCreateModal.value = false
    message.success('团队创建成功！')
  } catch { message.error('创建失败') }
  finally { creating.value = false }
}

const handleTeamAction = (key: string, team: Team) => {
  if (key === 'edit') {
    editForm.value = { id: team.id, name: team.name, description: (team as any).description || '' }
    showEditModal.value = true
  } else if (key === 'delete') {
    dialog.warning({
      title: '解散团队',
      content: `确定解散「${team.name}」？此操作不可撤销。`,
      positiveText: '解散',
      negativeText: '取消',
      onPositiveClick: async () => {
        await teamStore.deleteTeam(team.id)
        if (selectedTeam.value?.id === team.id) selectedTeam.value = null
        membersReady.value = false
        message.success('团队已解散')
      }
    })
  }
}

const handleEditTeam = async () => {
  try {
    await teamApi.updateTeam(editForm.value.id, { name: editForm.value.name, description: editForm.value.description })
    await loadTeams()
    if (selectedTeam.value?.id === editForm.value.id) {
      selectedTeam.value = teamStore.teams.find(t => t.id === editForm.value.id) || selectedTeam.value
    }
    showEditModal.value = false
    message.success('已更新')
  } catch { message.error('更新失败') }
}

const handleInviteMember = async () => {
  if (!inviteForm.value.username || !selectedTeam.value) { message.error('请输入用户名'); return }
  inviting.value = true
  try {
    await teamApi.inviteMember({ teamId: selectedTeam.value.id, username: inviteForm.value.username, role: inviteForm.value.role })
    await loadMembers(selectedTeam.value.id)
    inviteForm.value = { username: '', role: 'member' }
    showInviteModal.value = false
    message.success('邀请成功！')
  } catch (err: any) { message.error(err.message || '邀请失败') }
  finally { inviting.value = false }
}

const handleRemoveMember = async (member: any) => {
  if (!selectedTeam.value) return
  dialog.warning({
    title: '移除成员',
    content: `确定将 ${memberDisplayName(member)} 移出团队？`,
    positiveText: '移除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await teamApi.removeMember(selectedTeam.value!.id, member.userId)
      await loadMembers(selectedTeam.value!.id)
      message.success(`已移除 ${memberDisplayName(member)}`)
    }
  })
}
</script>

<style scoped>
.teams-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-size: 15px; font-weight: 600; color: var(--text-primary); }
.header-col { display: flex; flex-direction: column; gap: 2px; }
.team-desc { font-size: 11px; color: var(--text-muted); font-weight: 400; }
.s-icon { width: 14px; height: 14px; color: var(--text-muted); }

.team-list { display: flex; flex-direction: column; gap: 8px; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.pager { display: flex; justify-content: center; margin-top: 14px; }
.team-card { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 12px; cursor: pointer; transition: all .2s; border: 1px solid transparent; }
.team-card:hover { background: rgba(128,128,128,0.03); border-color: var(--border-color); }
.team-card.active-team { background: rgba(16,185,129,0.06); border-color: rgba(16,185,129,0.25); }
.team-info { flex: 1; display: flex; flex-direction: column; }
.team-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.team-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.member-list { display: flex; flex-direction: column; gap: 6px; }
.member-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 10px; background: rgba(128,128,128,0.02); border: 1px solid var(--border-light); }
.member-info { flex: 1; display: flex; flex-direction: column; }
.member-name { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.member-username { font-size: 11px; color: var(--text-muted); }
</style>

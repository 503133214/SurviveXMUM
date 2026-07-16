import axios from 'axios';
import {ElMessage} from "element-plus";
import {ref} from "vue";
const authItemName="token";
// 登录态版本号：写入/清除 token 时自增，供组件（如 Header）即时响应同一标签页内的登录/登出，
// 无需等待轮询或 storage 事件（storage 事件只在其它标签页触发，同标签页不会触发）。
export const authVersion = ref(0);
const defaultFailure=(message,code,url)=>{
    console.warn(`请求地址：${url},状态码：${code},错误信息: ${message}`)
    ElMessage.warning(message)
}
const defaultError=(err)=>{
    console.warn(err)
    ElMessage.warning("发生了一些错误，请联系管理员")
}
function parseJWTExpire(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.exp * 1000; // JWT exp 是秒，转换为毫秒
    } catch (e) {
        return Date.now() + 24 * 60 * 60 * 1000; // 解析失败则使用默认24小时
    }
}
function storeAccessToken(token) {
    const expire = parseJWTExpire(token);
    const authObj = { token: token, expire: expire };
    const str = JSON.stringify(authObj);
    localStorage.setItem(authItemName, str);
    authVersion.value++;
}
function takeAccessToken(){
    const token=localStorage.getItem(authItemName);
    if(!token){
        return null;
    }
    const authObj=JSON.parse(token);
    if(authObj.expire<=Date.now()){
        deleteAccessToken();
        ElMessage.warning("登录已过期，请重新登录")
        return null
    }
    return authObj.token; // 返回有效的token
}
function deleteAccessToken(){
    localStorage.removeItem(authItemName)
    authVersion.value++;
}
function internalPost(url,data,headers,success,failure,error=defaultError){
    axios.post(url, data, { headers: headers }).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function internalGet(url,headers,success,failure,error=defaultError){
    axios.get(url, { headers: headers}).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function internalPut(url,data,headers,success,failure,error=defaultError){
    axios.put(url, data, { headers: headers }).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function internalDelete(url,headers,success,failure,error=defaultError){
    axios.delete(url, { headers: headers }).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function accessHeader(){
    const token = takeAccessToken()
    return token ?{
        'Authorization': `Bearer ${token}`
    }:{}
}
function get(url,success,failure=defaultFailure){
    internalGet(url,accessHeader(),success,failure)
}
function post(url,data,success,failure=defaultFailure){
    internalPost(url,data,accessHeader(),success,failure)
}
function put(url,data,success,failure=defaultFailure){
    internalPut(url,data,accessHeader(),success,failure)
}
function remove(url,success,failure=defaultFailure){
    internalDelete(url,accessHeader(),success,failure)
}
function queryString(params){
    const query = new URLSearchParams()
    Object.entries(params || {}).forEach(([key,value]) => {
        if (value !== '' && value !== null && value !== undefined) query.set(key,String(value))
    })
    return query.toString()
}
function uploadImage(file,onProgress,success,failure=defaultFailure){
    const formData = new FormData()
    formData.append('file', file)
    axios.post('/wiki/image', formData, {
        headers: accessHeader(),
        timeout: 60000,
        onUploadProgress: (event) => {
            if (!event.total || !onProgress) return
            onProgress(Math.round((event.loaded * 100) / event.total))
        }
    }).then(({data}) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, '/wiki/image')
        }
    }).catch((err) => {
        const message = err.response?.data?.message
            || (err.code === 'ECONNABORTED' ? '图片上传超时，请重试' : '图片上传失败，请稍后重试')
        failure(message, err.response?.status || -1, '/wiki/image')
    })
}
function unauthorized(){
    return !takeAccessToken()
}
function login(username,password,success,failure=defaultFailure){
    internalPost("/login",{
        userEmail: username,
        password: password
    },{
        'Content-Type': 'application/json'
    },(data)=>{
        storeAccessToken(data.token)
        ElMessage.success(`登录成功,欢迎${username}！`)
        success(data)
    },failure)
}

function register(userEmail,password,code,success,failure=defaultFailure){
    internalPost("/register",{
        userEmail: userEmail,
        password: password,
        code: code
    },{
        'Content-Type': 'application/json'
    },(data)=>{
        storeAccessToken(data.token)
        ElMessage.success('注册成功！')
        success(data)
    },failure)
}

function resetPassword(userEmail,newPassword,code,success,failure=defaultFailure){
    internalPost("/password/reset",{
        userEmail: userEmail,
        newPassword: newPassword,
        code: code
    },{
        'Content-Type': 'application/json'
    },(data)=>{
        ElMessage.success('密码重置成功，请使用新密码登录')
        success(data)
    },failure)
}

function sendCode(email,type,success,failure=defaultFailure){
    internalPost(`/send/code?email=${encodeURIComponent(email)}&type=${type}`,{},{
        'Content-Type': 'application/json'
    },success,failure)
}
function logout(success, failure = defaultFailure, error = defaultError) {
    // 检查token是否存在且有效
    if (!takeAccessToken()) {
        failure('未找到登录令牌或令牌已过期', -1, '/login/logout');
        return;
    }
    internalPost(
        '/login/logout',
        {},
        accessHeader(),
        (responseData) => {
            deleteAccessToken(); // 登出成功后删除token
            success(responseData);
        },
        failure,
        error
    );
}
// ---- Wiki 投稿 / 审核 ----
function submitRevision(payload, success, failure = defaultFailure) {
    post('/wiki/revision', payload, success, failure)
}
function getMyRevisions(success, failure = defaultFailure) {
    get('/wiki/revision/mine', success, failure)
}
function getMyRevision(id, success, failure = defaultFailure) {
    get(`/wiki/revision/${id}`, success, failure)
}
function adminListRevisions(params, success, failure = defaultFailure) {
    // 兼容旧调用：可传字符串 status，或对象 {status, from, to, keyword}
    const q = typeof params === 'string' ? { status: params } : { ...(params || {}) }
    if (!q.status) q.status = 'PENDING'
    get(`/admin/revisions?${queryString(q)}`, success, failure)
}
function adminGetRevision(id, success, failure = defaultFailure) {
    get(`/admin/revision/${id}`, success, failure)
}
function adminApproveRevision(id, success, failure = defaultFailure) {
    post(`/admin/revision/${id}/approve`, {}, success, failure)
}
function adminRejectRevision(id, comment, success, failure = defaultFailure) {
    post(`/admin/revision/${id}/reject`, { comment }, success, failure)
}
function adminRevisionCounts(success, failure = defaultFailure) {
    get('/admin/revisions/counts', success, failure)
}
// ---- 超管改判（已通过/已驳回的管理）----
function adminReapproveRevision(id, success, failure = defaultFailure) {
    post(`/admin/revision/${id}/reapprove`, {}, success, failure)
}
function adminRevokeRevision(id, comment, success, failure = defaultFailure) {
    post(`/admin/revision/${id}/revoke`, { comment }, success, failure)
}
function adminUpdateRevisionComment(id, comment, success, failure = defaultFailure) {
    put(`/admin/revision/${id}/comment`, { comment }, success, failure)
}
function adminPurgeRevision(id, success, failure = defaultFailure) {
    remove(`/admin/revision/${id}`, success, failure)
}
function adminListUserRevisions(userId, success, failure = defaultFailure) {
    get(`/admin/users/${userId}/revisions`, success, failure)
}
function adminListUsers(query, success, failure = defaultFailure) {
    get(`/admin/users?${queryString(query)}`, success, failure)
}
function adminCreateUser(payload, success, failure = defaultFailure) {
    post('/admin/users', payload, success, failure)
}
function adminUpdateUser(id, payload, success, failure = defaultFailure) {
    put(`/admin/users/${id}`, payload, success, failure)
}
function adminDeleteUser(id, success, failure = defaultFailure) {
    remove(`/admin/users/${id}`, success, failure)
}
function adminRestoreUser(id, success, failure = defaultFailure) {
    post(`/admin/users/${id}/restore`, {}, success, failure)
}
function adminListPages(query, success, failure = defaultFailure) {
    get(`/admin/pages?${queryString(query)}`, success, failure)
}
function adminGetPage(id, success, failure = defaultFailure) {
    get(`/admin/page/${id}`, success, failure)
}
function adminCreatePage(payload, success, failure = defaultFailure) {
    post('/admin/pages', payload, success, failure)
}
function adminUpdatePage(id, payload, success, failure = defaultFailure) {
    put(`/admin/page/${id}`, payload, success, failure)
}
function adminDeletePage(id, success, failure = defaultFailure) {
    remove(`/admin/page/${id}`, success, failure)
}
function adminRestorePage(id, success, failure = defaultFailure) {
    post(`/admin/page/${id}/restore`, {}, success, failure)
}

// ---- 站内通知 ----
function getNotifications(success, failure = defaultFailure) {
    get('/notifications', success, failure)
}
function getUnreadCount(success, failure = defaultFailure) {
    // 纯后台轮询（Header 每 10s 一次）：网络层错误必须静默，
    // 否则部署重启后端的十几秒里所有打开的标签页会连环弹错误提示
    internalGet('/notifications/unread-count', accessHeader(), success, failure, () => {})
}
function readNotification(id, success, failure = defaultFailure) {
    post(`/notifications/${id}/read`, {}, success, failure)
}
function readAllNotifications(success, failure = defaultFailure) {
    post('/notifications/read-all', {}, success, failure)
}

// ---- 收藏 / 浏览历史（DocPage 用；FavoritesPage 沿用其自带的裸 get/post）----
function docFavoriteCheck(path, success, failure = defaultFailure) {
    // 打开文档时的后台查询：网络层错误静默（星标状态查不到就保持未收藏即可）
    internalGet(`/user/favorites/check?path=${encodeURIComponent(path)}`, accessHeader(), success, failure, () => {})
}
function docFavoriteAdd(path, notifyUpdates, success, failure = defaultFailure) {
    const url = '/user/favorites'
    internalPost(url, { path, notifyUpdates: !!notifyUpdates }, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '收藏操作失败，请检查网络后重试', err.response?.status || -1, url))
}
function docFavoriteRemove(id, success, failure = defaultFailure) {
    const url = `/user/favorites/${id}/remove`
    internalPost(url, {}, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '收藏操作失败，请检查网络后重试', err.response?.status || -1, url))
}
function docFavoriteUpdateNotification(id, notifyUpdates, success, failure = defaultFailure) {
    const url = `/user/favorites/${id}/notification`
    internalPut(url, { notifyUpdates }, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '关注设置失败，请检查网络后重试', err.response?.status || -1, url))
}
function recordHistory(path, success = () => {}, failure = () => {}) {
    // 纯后台埋点：网络层错误也静默
    internalPost('/user/history', { path }, accessHeader(), success, failure, () => {})
}

// ---- 页面公开版本历史 ----
function getPageRevisionHistory(path, success, failure = defaultFailure) {
    const url = `/wiki/page/revisions?path=${encodeURIComponent(path)}`
    internalGet(url, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '版本历史加载失败，请检查网络后重试', err.response?.status || -1, url))
}
function getPageRevisionHistoryDetail(id, success, failure = defaultFailure) {
    const url = `/wiki/page/revisions/${id}`
    internalGet(url, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '版本详情加载失败，请检查网络后重试', err.response?.status || -1, url))
}
function adminPurgePageVersion(id, success, failure = defaultFailure) {
    remove(`/admin/page-version/${id}`, success, failure)
}

// ---- 反馈管理（后台）----
function adminListFeedback(query, success, failure = defaultFailure) {
    get(`/admin/feedback?${queryString(query)}`, success, failure)
}
function adminReplyFeedback(id, payload, success, failure = defaultFailure) {
    post(`/admin/feedback/${id}/reply`, payload, success, failure)
}

// ---- 分类管理 / 公告广播 / 审计日志（仅超管）----
function adminListCategories(success, failure = defaultFailure) {
    get('/admin/categories', success, failure)
}
function adminCreateCategory(payload, success, failure = defaultFailure) {
    post('/admin/categories', payload, success, failure)
}
function adminUpdateCategory(id, payload, success, failure = defaultFailure) {
    put(`/admin/categories/${id}`, payload, success, failure)
}
function adminDeleteCategory(id, success, failure = defaultFailure) {
    remove(`/admin/categories/${id}`, success, failure)
}
function adminBroadcast(payload, success, failure = defaultFailure) {
    post('/admin/broadcast', payload, success, failure)
}
function adminAuditQuery(query, success, failure = defaultFailure) {
    get(`/admin/audit?${queryString(query)}`, success, failure)
}
// ---- 彻底删除（仅超管，不可恢复）----
function adminPurgePage(id, success, failure = defaultFailure) {
    remove(`/admin/page/${id}/purge`, success, failure)
}
function adminPurgeUser(id, success, failure = defaultFailure) {
    remove(`/admin/users/${id}/purge`, success, failure)
}
function adminDeleteFeedback(id, success, failure = defaultFailure) {
    remove(`/admin/feedback/${id}`, success, failure)
}

// ---- 写文章草稿 ----
// 支持自定义 error（网络层错误）处理：自动保存需完全静默，不能弹全局错误提示
function saveDraft(payload, success, failure = defaultFailure, error = defaultError) {
    internalPost('/wiki/drafts', payload, accessHeader(), success, failure, error)
}
function listDrafts(success, failure = defaultFailure) {
    get('/wiki/drafts', success, failure)
}
function getDraft(id, success, failure = defaultFailure) {
    get(`/wiki/drafts/${id}`, success, failure)
}
function getDraftByPath(path, success, failure = defaultFailure) {
    get(`/wiki/drafts/by-path?path=${encodeURIComponent(path)}`, success, failure)
}
function deleteDraft(id, success, failure = defaultFailure) {
    remove(`/wiki/drafts/${id}`, success, failure)
}

// ---- 贡献榜 / 贡献者主页（公开）----
function getContributors(success, failure = defaultFailure) {
    get('/contributors', success, failure)
}
function getContributorProfile(id, success, failure = defaultFailure) {
    get(`/contributors/${id}`, success, failure)
}
function getPageContributors(path, success, failure = defaultFailure) {
    const url = `/contributors/page?path=${encodeURIComponent(path)}`
    internalGet(url, accessHeader(), success, failure,
        (err) => failure(err.response?.data?.message || '贡献者信息加载失败，请检查网络后重试', err.response?.status || -1, url))
}

// ---- 致谢墙（公开读；管理仅超管）----
function getWall(success, failure = defaultFailure) {
    get('/wall', success, failure)
}
function adminListWall(success, failure = defaultFailure) {
    get('/admin/wall', success, failure)
}
function adminCreateWall(payload, success, failure = defaultFailure) {
    post('/admin/wall', payload, success, failure)
}
function adminUpdateWall(id, payload, success, failure = defaultFailure) {
    put(`/admin/wall/${id}`, payload, success, failure)
}
function adminDeleteWall(id, success, failure = defaultFailure) {
    remove(`/admin/wall/${id}`, success, failure)
}

export {get,unauthorized,post,put,remove,accessHeader,login,logout,takeAccessToken,register,resetPassword,sendCode,
    uploadImage,submitRevision,getMyRevisions,adminListRevisions,adminGetRevision,adminApproveRevision,adminRejectRevision,
    adminRevisionCounts,adminListUserRevisions,getMyRevision,
    adminReapproveRevision,adminRevokeRevision,adminUpdateRevisionComment,adminPurgeRevision,
    adminListUsers,adminCreateUser,adminUpdateUser,adminDeleteUser,adminRestoreUser,
    adminListPages,adminGetPage,adminCreatePage,adminUpdatePage,adminDeletePage,adminRestorePage,
    getNotifications,getUnreadCount,readNotification,readAllNotifications,
    docFavoriteCheck,docFavoriteAdd,docFavoriteRemove,docFavoriteUpdateNotification,recordHistory,
    getPageRevisionHistory,getPageRevisionHistoryDetail,adminPurgePageVersion,
    adminListFeedback,adminReplyFeedback,
    getContributors,getContributorProfile,getPageContributors,
    getWall,adminListWall,adminCreateWall,adminUpdateWall,adminDeleteWall,
    saveDraft,listDrafts,getDraft,getDraftByPath,deleteDraft,
    adminListCategories,adminCreateCategory,adminUpdateCategory,adminDeleteCategory,
    adminBroadcast,adminAuditQuery,
    adminPurgePage,adminPurgeUser,adminDeleteFeedback}

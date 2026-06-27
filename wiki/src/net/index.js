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
function adminListRevisions(status, success, failure = defaultFailure) {
    get(`/admin/revisions?status=${encodeURIComponent(status || 'PENDING')}`, success, failure)
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

export {get,unauthorized,post,put,remove,accessHeader,login,logout,takeAccessToken,register,resetPassword,sendCode,
    uploadImage,submitRevision,getMyRevisions,getMyRevision,adminListRevisions,adminGetRevision,adminApproveRevision,adminRejectRevision,
    adminListUsers,adminCreateUser,adminUpdateUser,adminDeleteUser,adminRestoreUser,
    adminListPages,adminGetPage,adminCreatePage,adminUpdatePage,adminDeletePage,adminRestorePage}

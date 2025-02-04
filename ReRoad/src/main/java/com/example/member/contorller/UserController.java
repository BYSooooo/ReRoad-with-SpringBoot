package com.example.member.contorller;

import com.example.board.service.BoardService;
import com.example.board.vo.BoardVo;
import com.example.board.vo.CommentVo;
import com.example.member.service.MailService;
import com.example.member.service.UserService;
import com.example.member.vo.MailVo;
import com.example.member.vo.UserAccount;
import com.example.member.vo.UserVo;
import com.example.util.FileUploadService;
import org.attoparser.IDocumentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.parameters.P;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.spring5.context.IThymeleafBindStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Controller
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FileUploadService fileUploadService;


    @Autowired
    public HttpSession session;

    @Autowired
    public MailService mailService;

    @Autowired
    private AuthenticationManager authenticationManager;

    //로그인 폼
    @GetMapping("/loginForm")
    public String login(Model model) {
        model.addAttribute("content","views/member/loginForm");
        return "/templates"; }

    // 로그인 성공
    @PostMapping("/loginOk")
    public String loginSuccess(@AuthenticationPrincipal UserAccount prin, Model model) {

        String userId = prin.getUser().getUserId();
        UserVo user = this.userService.getInfo(userId);


        System.out.println("userId : " +userId);
        // 로그인 후 세션에 UserAccount(UserVo+Role) 객체 등록
        session.setAttribute("loginUser", user);


        return "redirect:/main";
    }

    //로그인이 실패했을 경우
    @PostMapping("/loginFail")
    public String forFailer(@RequestParam ("username") String userId,  Model model) {
        // 아이디를 입력하지 않았을 경우

        if(userId.equals("")){

            model.addAttribute("failMessage", "아이디를 입력해주세요");
        } else {
            // 아이디를 입력했을 경우 입력한 ID 값을 가져와서 DB에서 중복 검사
            int checkNum = this.userService.checkId(userId);
            // 중복한 값이 있다면 비밀번호 오류로 안내
            // 중복한 값이 없다면 가입하지 않은 이메일로 안내
            if (checkNum == 1) {
                model.addAttribute("failMessage","비밀번호가 잘못되었습니다.입력하신 정보를 확인해주세요.");
            } else {
                model.addAttribute("failMessage", "해당 이메일로 가입한 회원 정보를 찾을 수 없습니다.");
            }
            model.addAttribute("content","views/member/loginForm");
        }
        return "/templates";
    }

    @GetMapping("/joinUser")
    public String forJoin(Model model) {
        model.addAttribute("content","views/member/JoinUser");
        return "/templates";}

    @GetMapping("/forgetPwd")
    public String forForgetPwd(Model model) {
        model.addAttribute("content","views/member/forgetPwd");
        return "/templates";}

    // 회원 가입 과정에서 아이디 중복 체크
    @RequestMapping(value="/checkId", method = RequestMethod.POST)
    public @ResponseBody String checkIdAjax(@RequestParam("userId") String requestId) {
        String inputId = requestId.trim();
        int checkNum = this.userService.checkId(inputId);
        String checkResult = "";
        // 중복된 아이디는 1 = 가입 불가
        // 없는 아이디는 0 = 가입 가능
        if (checkNum > 0) {
            checkResult = "false";
        } else if (checkNum == 0) {
            checkResult = "true";
        }
        return checkResult;
    }

    // 회원 가입 과정에서 닉네임 중복 체크
    @RequestMapping(value="/checkNick", method = RequestMethod.POST)
    public @ResponseBody String checkNickAjax(@RequestParam("userNick") String requestNick) {
        String inputNick = requestNick.trim();
        int checkNick = this.userService.checkNick(inputNick);
        String nickResult = "";
        // 중복된 닉네임은 1 = 가입 불가
        // 없는 닉네임은 0 = 가입 가능
        if (checkNick > 0) {
            nickResult = "false";
        } else if (checkNick == 0) {
            nickResult = "true";
        }
        return nickResult;
    }

    //회원가입 2번째 창으로 이동
    @PostMapping("/moveJoinFormSe")
    public String moveSeForm(@RequestParam ("userId") String userId, Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("content","views/member/JoinUserSe");
        return "/templates";
    }

    //회원가입
    @RequestMapping(value="/joinUserRequest", method = RequestMethod.POST)
    public String joinUserRequest(@Valid @ModelAttribute UserVo user, BindingResult bindingResult, Model model) {
        //DB 유효성 체크 결과 에러가 발생할 경우 가입폼으로 돌아감
        if (bindingResult.hasErrors()) {
            Map<String, String> map = userService.validate(bindingResult);
            for(String key: map.keySet()) {
                model.addAttribute(key,map.get(key));
                // 가입 실패 후 되돌아가는 화면으로 아이디 값을 다시 되돌려줌
                model.addAttribute("userId",user.getUserId());
                model.addAttribute("content","views/member/JoinUserSe");
            }return "/templates";
        } else {
            // 에러가 없을 경우 Password 암호화 후 DB에 등록
            UserVo verifyUser = new UserVo();
            verifyUser.setUserId(user.getUserId());
            verifyUser.setUserPwd(passwordEncoder.encode(user.getUserPwd()));
            verifyUser.setUserNick(user.getUserNick());
            verifyUser.setRole("ROLE_MEMBER");

            this.userService.registUser(verifyUser);
            model.addAttribute("content","views/member/joinSuccess");
            return "/templates";
        }

    }
    //인증 메일 발송
    @PostMapping("/verifyEmail")
    public @ResponseBody String sendEmail(@RequestParam("mail") String email) {
        String key="";
        Random random = new Random();
        for(int i = 0; i<3; i++) {
            int index = random.nextInt(25)+65;
            key += (char)index;
        }
        int numIndex = random.nextInt(9999)+1000;
        key += numIndex;
        MailVo mail = new MailVo();
        mail.setAddress(email);
        mail.setTitle("ReRoad 회원 가입을 위한 인증 메일입니다.");
        mail.setMessage("인증 번호는 "+ key + "입니다.");

        // 1이면 발송 성공 = key 반환, 0이면 실패 = "Error" 반환
        int result = this.mailService.sendMail(mail);
        System.out.println(result);
        if(result == 1) {
            return key;
        } else {
            return "Error";
        }


    }

    // 임시 비밀번호 발급
    @PostMapping("/sendTempPwd")
    public String sendTempPwd(@RequestParam("userId") String email, Model model) {
        String tempPwd = "";
        Random random = new Random();
        for (int i=0; i<5; i++) {
            int index = random.nextInt(25)+65;
            tempPwd +=(char)index;
        }
        int numIndex = random.nextInt(9999)+1000;
        tempPwd += numIndex;
        MailVo mail = new MailVo();
        mail.setAddress(email);
        mail.setTitle("ReRoad 회원의 임시비밀번호 발급 메일입니다.");
        mail.setMessage("임시 비밀 번호는 " + tempPwd + "입니다. 로그인 후 반드시 비밀번호를 변경하시기 바랍니다.");
        this.mailService.sendMail(mail);
        System.out.println(tempPwd);

        UserVo user = new UserVo();
        user.setUserId(email);
        user.setUserPwd(passwordEncoder.encode(tempPwd));
        this.userService.updateTempPwd(user);
        model.addAttribute("content","views/member/forgetPwdSuccess");

        return "/templates";
    }



    // 권한이 없는 경로로 접근했을 경우
    @PostMapping("/accessDenied")
    public void deniedMessage() {

    }


    // 관리자의 사용자 정보 조회
    @GetMapping("/admin/listUser")
    public String listUser(Model model) {
        List<UserVo> users = this.userService.retrieveUserList();
        model.addAttribute("userList", users);
        model.addAttribute("content","views/member/listUser");
        return "/templates";
    }


    // 사용자 정보 조회 ajax
    @GetMapping("/admin/getList")
    public @ResponseBody List<UserVo> userList() {
        List<UserVo> users = this.userService.retrieveUserList();
        return users;
    }


    // 선택회원 강제 탈퇴
    @RequestMapping(value = "/admin/deleteUserList", method = RequestMethod.POST)
    public @ResponseBody Map<String, Object> userListDelete(@RequestBody String[] valueArr, Model model) {
        int size = valueArr.length;
        for (int i = 0; i<size; i++){
            this.userService.removeUserForce(valueArr[i]);
        }
        Map<String, Object> map = new HashMap<String, Object>();
        List<UserVo> users = this.userService.retrieveUserList();
        map.put("userList", users);
        return map;
    }

    //비밀번호 일치 확인 후 삭제 페이지 이동
    @PostMapping("/member/pwdCheck")
    public String pwdCheck(Authentication authentication,@Valid @RequestParam String userPwd, Model model) {
        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        String userId = userDetails.getUsername();

        String checkPwd = this.userService.checkPwd(userId);

        boolean result = passwordEncoder.matches(userPwd, checkPwd);
        model.addAttribute("result", result);

        if (result) {
            model.addAttribute("content","views/member/deleteUser");
            return "/templates";
        } else {
            model.addAttribute("content","views/member/pwdCheckFail");

            return "/templates";
        }
    }

    @GetMapping("/member/exitUser")
    public String exitUser(Model model) {
        model.addAttribute("content","views/member/pwdCheck");
        return "/templates";
    }


    //회원 자진 탈퇴 시 회원정보 삭제
    @PostMapping("/member/deleteUser")
    public String deleteUser(Authentication authentication){
        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        String userId = userDetails.getUsername();

        this.userService.removeUser(userId);

        return "redirect:/logout";
    }

    //회원 정보 상세 조회
    @GetMapping("/member/modifyUserForm")
    public String modifyUserForm(Authentication authentication, Model model) {

        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        String userId = userDetails.getUsername();

        UserVo user = this.userService.retrieveUser(userId);

        model.addAttribute("user", user);
        model.addAttribute("content","views/member/modifyUser");
        return "/templates";
    }

    // 회원정보수정
    @PostMapping("/member/modifyUser")
    public String modifyUser(@Valid @ModelAttribute UserVo user, BindingResult bindingResult,
                             Model model, HttpServletRequest request,
                             @AuthenticationPrincipal UserAccount prin) {

        //DB 유효성 체크 결과 에러가 발생할 경우 수정폼으로 돌아감
        if (bindingResult.hasErrors()) {
            Map<String, String> map = userService.validate(bindingResult);
            for(String key: map.keySet()) {
                model.addAttribute(key,map.get(key));
            }
            String userId = prin.getUsername();
            UserVo user1 = this.userService.retrieveUser(userId);

            model.addAttribute("user", user1);
            model.addAttribute("content","views/member/modifyUser");
            return "/templates";
        } else {
            UserVo modifyUser = new UserVo();
            modifyUser.setUserId(user.getUserId());
            modifyUser.setUserPwd(passwordEncoder.encode(user.getUserPwd()));
            modifyUser.setUserNick(user.getUserNick());
            modifyUser.setPhotoOrigin(user.getPhoto().getOriginalFilename());

            String imgname = null;
            MultipartFile photo = user.getPhoto();
            if (!user.getPhoto().getOriginalFilename().isEmpty()) {
                imgname = fileUploadService.restore(photo, request);

            }
            modifyUser.setPhotoSys(imgname);

            this.userService.modifyUser(modifyUser);

            //Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUserId(), user.getUserPwd()));
            //SecurityContextHolder.getContext().setAuthentication(authentication);
            String userId = prin.getUsername();
            UserVo user1 = this.userService.getInfo(userId);

            session.setAttribute("loginUser", user1);



//            UserVo user2 = this.userService.getInfo(userId);
//            String userNick = user2.getUserNick();
//            String userRole = user2.getRole();

//            session.setAttribute("loginUser", prin.getUser().getUserNick());
//            session.setAttribute("loginUser", prin.getUser().getPhotoSys());


            UserVo user2 = this.userService.retrieveUser(userId);
            model.addAttribute("user", user2);

            List<BoardVo> board = this.boardService.retrieveRecentBoardList(userId);
            model.addAttribute("board", board);

            List<CommentVo> com = this.boardService.retrieveUserComList(userId);
            model.addAttribute("com", com);

            model.addAttribute("content","views/member/myPage");
            return "/templates";
        }
    }

    //마이페이지 회원조회
    @GetMapping("/member/myPage")
    public String mypageForm(Authentication authentication, Model model) {

        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        String userId = userDetails.getUsername();

        UserVo user = this.userService.retrieveUser(userId);
        model.addAttribute("user", user);

        List<BoardVo> board = this.boardService.retrieveRecentBoardList(userId);
        model.addAttribute("board", board);

        List<CommentVo> com = this.boardService.retrieveUserComList(userId);
        model.addAttribute("com", com);

        model.addAttribute("content","views/member/myPage");
        return "/templates";
    }

    //관리자 마이페이지
    @GetMapping("/admin/myPage")
    public String adminMyPage(Authentication authentication, Model model) {

        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        String userId = userDetails.getUsername();

        UserVo user = this.userService.retrieveUser(userId);
        model.addAttribute("user", user);

        List<BoardVo> board = this.boardService.retrieveRecentBoardList(userId);
        model.addAttribute("board", board);

        List<CommentVo> com = this.boardService.retrieveUserComList(userId);
        model.addAttribute("com", com);

        model.addAttribute("content","views/member/adminMyPage");
        return "/templates";
    }
}
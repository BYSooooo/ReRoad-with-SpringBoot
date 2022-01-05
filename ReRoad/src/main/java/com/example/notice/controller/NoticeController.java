package com.example.notice.controller;

import com.example.common.FileVO;
import com.example.notice.service.NoticeService;
import com.example.notice.vo.NoticeVO;
import com.example.util.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    private String savePath = "/upload/";

    @Autowired
    private FileUploadService fileUploadService;

    //공지 목록조회 페이지로 이동
    @GetMapping("/noticelist")
    public String noticeList() {

        return "views/notice/noticeList.html";


    }
    // 공지글 상세보기
    @GetMapping("/noticedetail/{noticeNo}")
    public String detailnotice(@PathVariable  int noticeNo, Model model, HttpServletRequest request, HttpServletResponse response) {

        Cookie[] cookies = request.getCookies();

        //비교하기 위해 새로운 쿠키
        Cookie viewCookie = null;

        //쿠키가 있을 경우,
        if (cookies != null && cookies.length > 0)
        {
            for (int i =0; i < cookies.length; i++)
            {
                // Cookie의 name이 cookie + noticeNo과 일치하는 쿠키를 viewCookie에 넣어줌
                if (cookies[i].getName().equals("cookie"+noticeNo)) {
                    viewCookie = cookies[i];
                }
            }
        }

        if (viewCookie == null) {

            // 쿠키 생성(이름, 값)
            Cookie newCookie = new Cookie("cookie"+noticeNo, "|" + noticeNo + "|");

            // 쿠키 추가
            response.addCookie(newCookie);

            // 쿠키를 추가 시키고 조회수 증가시킴
            this.noticeService.uphitCount(noticeNo);
        }
        // viewCookie가 null이 아닐경우 쿠키가 있으므로 조회수 증가 로직을 처리하지 않음.
        else {
            // 쿠키 값 받아옴.
            String value = viewCookie.getValue();
        }
        NoticeVO notice = this.noticeService.retrieveNotice(noticeNo);
        model.addAttribute("notice", notice);
        return "views/notice/noticeDetail";
    }

    // 공지글 수정
    // 수정폼불러오기
    @GetMapping("/admin/noticemodifyform/{noticeNo}")
    public String noticeModifyForm(@PathVariable int noticeNo, Model model) {

        NoticeVO notice = this.noticeService.retrieveNotice(noticeNo);
        notice.setNoticeNo(noticeNo);
        model.addAttribute("notice",notice);
        return "views/notice/noticeModifyForm";
    }

    //게시글 수정
    @PostMapping("/admin/modifynotice")
    public String noticeModify(@Valid NoticeVO notice,@RequestParam int noticeNo,
                               @RequestPart(value = "noticeFileInput", required = false) List<MultipartFile> files,
                               HttpServletRequest request) {

        NoticeVO newNotice = new NoticeVO();

        newNotice.setNoticeTitle(notice.getNoticeTitle());
        newNotice.setNoticeContent(notice.getNoticeContent());
        newNotice.setNoticeNo(noticeNo);

        List<FileVO> noticeFileVO = new ArrayList<FileVO>();

        for (MultipartFile file : files) {
            FileVO noticefile = new FileVO();
            noticefile.setFileOrigin(file.getOriginalFilename());
            String noticeFileName = null;
            if (!file.getOriginalFilename().isEmpty()) {
                noticeFileName = fileUploadService.noticeStore(file, request);
                noticefile.setFileSys(noticeFileName);
                noticefile.setFileSize(file.getSize());

                noticeFileVO.add(noticefile);

            }
        }
        newNotice.setNoticeFileList(noticeFileVO);
        this.noticeService.modifyNotice(newNotice);


        return "redirect:/noticelist";

    }

    // 공지글 작성
    // 작성폼불러오기
    @GetMapping("/admin/noticewriteform")
    public String noticeWriteForm( ) {

        return "views/notice/noticeWriteForm";
    }

    //게시글 작성
    @PostMapping("/admin/writenotice")
    public String noticeWrite(@Valid NoticeVO notice,
                              @AuthenticationPrincipal User principal,
                              @RequestPart(value = "noticeFileInput", required = false) List<MultipartFile> files,
                              HttpServletRequest request) {

        NoticeVO newNotice = new NoticeVO();
        newNotice.setNoticeTitle(notice.getNoticeTitle());
        newNotice.setNoticeContent(notice.getNoticeContent());
        newNotice.setUserId( principal.getUsername());

        List<FileVO> noticeFileVO = new ArrayList<FileVO>();
        for(MultipartFile file : files) {
            FileVO noticefile = new FileVO();
            noticefile.setFileOrigin(file.getOriginalFilename());
            String noticeFileName = null;
            if (!file.getOriginalFilename().isEmpty()) {
                noticeFileName = fileUploadService.noticeStore(file, request);
                noticefile.setFileSys(noticeFileName);
                noticefile.setFileSize(file.getSize());

                noticeFileVO.add(noticefile);

            }
        }
        newNotice.setNoticeFileList(noticeFileVO);
        this.noticeService.createNotice(newNotice);

        return "redirect:/noticelist";
    }

    //파일 다운로드
    @GetMapping("/fileDownload/{fileNo}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable int fileNo) throws MalformedURLException {

        FileVO file = this.noticeService.retrieveNoticeFile(fileNo);
        String storedFileName = file.getFileSys();
        String uploadFileName = file.getFileOrigin();
        UrlResource resource = new UrlResource("file:" + savePath + storedFileName);
        log.info("uploadFileName = {}", uploadFileName);
        String encodedUploadFileName = UriUtils.encode(uploadFileName, StandardCharsets.UTF_8);
        String contentDispostion = "attachment; filename=\"" + encodedUploadFileName + "\"";

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, contentDispostion).body(resource);
    }
}
